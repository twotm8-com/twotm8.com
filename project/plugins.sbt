val BindgenVersion =
  sys.env.getOrElse("SN_BINDGEN_VERSION", "0.0.17")

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

addSbtPlugin("com.indoorvivants" % "bindgen-sbt-plugin" % BindgenVersion)
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.12")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.1")

val VcpkgVersion =
  sys.env.getOrElse("SBT_VCPKG_VERSION", "0.0.11")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("com.indoorvivants.vcpkg" % "sbt-vcpkg-native" % VcpkgVersion)
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")

libraryDependencySchemes ++= Seq(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always
)

addSbtPlugin("org.jmotor.sbt" % "sbt-dependency-updates" % "1.2.7")
