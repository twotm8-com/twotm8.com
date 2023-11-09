package twotm8.client

import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model.*
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.client3.*
import sttp.capabilities.Effect
import sttp.client3.monad.IdMonad
import sttp.monad.TryMonad
import scala.util.Try

import _root_.curl.all.*
import scala.scalanative.unsafe.*
import scala.collection.mutable.ArrayBuilder
import scala.scalanative.posix.string
import sttp.client3.internal.BodyFromResponseAs
import sttp.client3.internal.SttpFile
import _root_.curl.enumerations.CURLoption.CURLOPT_POSTFIELDS
import scala.io.Source

abstract class AbstractCurlBackend[F[_]](monad: MonadError[F], verbose: Boolean)
    extends SttpBackend[F, Any]:
  override implicit val responseMonad: MonadError[F] = monad

  override def close(): F[Unit] = monad.unit(())

  type PE = Any with Effect[F]

  val curl_handle = curl_easy_init()

  inline def OPT[T](opt: CURLoption, value: T) =
    check(curl_easy_setopt(curl_handle, opt, value))

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    Zone { implicit z =>

      if verbose then OPT(CURLoption.CURLOPT_VERBOSE, true)

      if request.tags.nonEmpty then
        responseMonad.error(
          new UnsupportedOperationException("Tags are not supported")
        )
      else
        val reqHeaders = request.headers
        reqHeaders
          .find(_.name == "Accept-Encoding")
          .foreach(h =>
            OPT(CURLoption.CURLOPT_ACCEPT_ENCODING, toCString(h.value))
          )
        // TODO: multipart body

        val finalizers = List.newBuilder[() => Unit]
        try
          finalizers += setHeaders(
            curl_handle,
            reqHeaders.map(h => h.name -> h.value).toMap
          )

          finalizers += (() => curl_easy_reset(curl_handle))

          // TODO: set method
          // TODO: set request body
          OPT(CURLoption.CURLOPT_URL, toCString(request.uri.toString))
          OPT(
            CURLoption.CURLOPT_TIMEOUT_MS,
            request.options.readTimeout.toMillis
          )

          val builder = Array.newBuilder[Byte]
          val (ptr, memory) = Captured.unsafe(builder)

          val headersBuilder = Array.newBuilder[Byte]
          val (headersPtr, headersMemory) = Captured.unsafe(headersBuilder)

          finalizers += (() => memory.deallocate())
          finalizers += (() => headersMemory.deallocate())

          val write_data_callback = CFuncPtr4.fromScalaFunction {
            (ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: Ptr[Byte]) =>
              val vec = !userdata.asInstanceOf[Ptr[ArrayBuilder[Byte]]]

              val newArr = new Array[Byte](nmemb.toInt)

              string.memcpy(newArr.at(0), ptr, nmemb)

              vec.addAll(newArr)

              nmemb * size
          }

          val write_headers_callback = CFuncPtr4.fromScalaFunction {
            (
                buffer: Ptr[Byte],
                size: CSize,
                nitems: CSize,
                userdata: Ptr[Byte]
            ) =>
              val vec = !userdata.asInstanceOf[Ptr[ArrayBuilder[Byte]]]

              for i <- 0 until nitems.toInt do vec.addOne(buffer(i))

              nitems * size

          }

          OPT(CURLoption.CURLOPT_WRITEFUNCTION, write_data_callback)
          OPT(CURLoption.CURLOPT_WRITEDATA, ptr)

          OPT(CURLoption.CURLOPT_HEADERFUNCTION, write_headers_callback)
          OPT(CURLoption.CURLOPT_HEADERDATA, headersPtr)

          setMethod(request.method)
          setRequestBody(request.body)

          check(curl_easy_perform(curl_handle))

          val responseBody = new String(builder.result())

          val headerLines =
            new String(headersBuilder.result()).linesIterator.toList

          val code = stackalloc[Long]()
          check(
            curl_easy_getinfo(
              curl_handle,
              CURLINFO.CURLINFO_RESPONSE_CODE,
              code
            )
          )

          val statusCode = StatusCode((!code).toInt)

          val statusText = headerLines.head.split(" ").last
          val headers = headerLines.tail.flatMap(parseHeaders)

          val metadata = ResponseMetadata(statusCode, statusText, headers)
          val body =
            bodyFromResponseAs(request.response, metadata, Left(responseBody))

          responseMonad.map(body) { b =>
            Response[T](
              body = b,
              code = statusCode,
              statusText = statusText,
              headers = headers,
              history = Nil,
              request = onlyMetadata(request)
            )
          }

        finally finalizers.result().foreach { f => f() }
        end try
      end if

    }

  private def parseHeaders(str: String): Seq[Header] =
    val array = str
      .split("\n")
      .filter(_.trim.length > 0)
    Seq(array*)
      .map { line =>
        val split = line.split(":", 2)
        if split.size == 2 then Header(split(0).trim, split(1).trim)
        else Header(split(0).trim, "")
      }
  end parseHeaders

  private def setMethod(method: Method)(using
      Zone
  ): F[CURLcode] =
    import CURLoption.*
    val m = method match
      case Method.GET => OPT(CURLOPT_HTTPGET, 1L)
      // TODO: check
      case Method.HEAD    => OPT(CURLOPT_HTTPHEADER, 1L)
      case Method.POST    => OPT(CURLOPT_POST, 1L)
      case Method.PUT     => OPT(CURLOPT_PUT, 1L)
      case Method.DELETE  => OPT(CURLOPT_CUSTOMREQUEST, c"DELETE")
      case Method.OPTIONS => OPT(CURLOPT_RTSP_REQUEST, 1L)
      case Method.PATCH   => OPT(CURLOPT_CUSTOMREQUEST, c"PATCH")
      case Method.CONNECT => OPT(CURLOPT_CONNECT_ONLY, 1L)
      case Method.TRACE   => OPT(CURLOPT_CUSTOMREQUEST, c"TRACE")
      case Method(m)      => OPT(CURLOPT_CUSTOMREQUEST, toCString(m))

    responseMonad.unit(m)
  end setMethod

  private def onlyMetadata[T, R](r: Request[T, R]) =
    val m = r.method
    val u = r.uri
    val h = r.headers
    new RequestMetadata:
      override val method: Method = m
      override val uri: Uri = u
      override val headers: Seq[Header] = h

  private lazy val bodyFromResponseAs =
    new BodyFromResponseAs[F, String, Nothing, Nothing]:
      override protected def withReplayableBody(
          response: String,
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[String] = response.unit

      override protected def regularIgnore(response: String): F[Unit] = ().unit

      override protected def regularAsByteArray(
          response: String
      ): F[Array[Byte]] = responseMonad.unit(response.getBytes())

      override protected def regularAsFile(
          response: String,
          file: SttpFile
      ): F[SttpFile] =
        responseMonad.unit(file)

      override protected def regularAsStream(
          response: String
      ): F[(Nothing, () => F[Unit])] =
        throw new IllegalStateException(
          "CurlBackend does not support streaming responses"
        )

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, ?],
          meta: ResponseMetadata,
          ws: Nothing
      ): F[T] = ws

      override protected def cleanupWhenNotAWebSocket(
          response: String,
          e: NotAWebSocketException
      ): F[Unit] = ().unit

      override protected def cleanupWhenGotWebSocket(
          response: Nothing,
          e: GotAWebSocketException
      ): F[Unit] = response

  private def makeHeaders(hd: Map[String, String])(using Zone) =
    var slist: Ptr[curl_slist] = null
    hd.foreach { case (k, v) =>
      slist = curl_slist_append(slist, toCString(s"$k:$v"))
    }
    slist

  private def setHeaders(handle: Ptr[CURL], hd: Map[String, String])(using
      Zone
  ) =
    val slist = makeHeaders(hd)
    check(curl_easy_setopt(handle, CURLoption.CURLOPT_HTTPHEADER, slist))
    () => curl_slist_free_all(slist)

  private def basicBodyToString(body: RequestBody[?]): String =
    body match
      case StringBody(b, _, _)   => b
      case ByteArrayBody(b, _)   => new String(b)
      case ByteBufferBody(b, _)  => new String(b.array)
      case InputStreamBody(b, _) => Source.fromInputStream(b).mkString
      case FileBody(f, _)        => Source.fromFile(f.toFile).mkString
      case NoBody                => new String("")
      case _ => throw new IllegalArgumentException(s"Unsupported body: $body")

  private def setRequestBody[R >: PE](body: RequestBody[R])(using
      Zone
  ): F[CURLcode] =
    body match // todo: assign to responseMonad object
      case b: BasicRequestBody =>
        val str = basicBodyToString(b)
        responseMonad.unit(OPT(CURLOPT_POSTFIELDS, toCString(str)))
      // case MultipartBody(parts) =>
      //   val mime = curl.mime
      //   parts.foreach { case p @ Part(name, partBody, _, headers) =>
      //     val part = mime.addPart()
      //     part.withName(name)
      //     val str = basicBodyToString(partBody)
      //     part.withData(str)
      //     p.fileName.foreach(part.withFileName(_))
      //     p.contentType.foreach(part.withMimeType(_))

      //     val otherHeaders = headers.filterNot(_.is(HeaderNames.ContentType))
      //     if otherHeaders.nonEmpty then
      //       val curlList = transformHeaders(otherHeaders)
      //       part.withHeaders(curlList.ptr)
      //       multiPartHeaders = multiPartHeaders :+ curlList
      //   }
      //   lift(curl.option(Mimepost, mime))
      case StreamBody(_) =>
        responseMonad.error(
          new IllegalStateException(
            "CurlBackend does not support stream request body"
          )
        )
      case NoBody =>
        responseMonad.unit(CURLcode.CURLE_OK)

end AbstractCurlBackend

private class CurlBackend(verbose: Boolean)
    extends AbstractCurlBackend[Identity](IdMonad, verbose) {}

object CurlBackend:
  def apply(verbose: Boolean = false): SttpBackend[Identity, Any] =
    new FollowRedirectsBackend[Identity, Any](
      new CurlBackend(verbose): SttpBackend[Identity, Any]
    )

private class CurlTryBackend(verbose: Boolean)
    extends AbstractCurlBackend[Try](TryMonad, verbose) {}

object CurlTryBackend:
  def apply(verbose: Boolean = false): SttpBackend[Try, Any] =
    new FollowRedirectsBackend[Try, Any](
      new CurlTryBackend(verbose): SttpBackend[Try, Any]
    )
