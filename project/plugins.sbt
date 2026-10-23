val BindgenVersion =
  sys.env.getOrElse("SN_BINDGEN_VERSION", "0.4.5")

val VcpkgVersion =
  sys.env.getOrElse("SBT_VCPKG_VERSION", "0.0.21")

addSbtPlugin("com.indoorvivants" % "bindgen-sbt-plugin" % BindgenVersion)
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("com.indoorvivants.vcpkg" % "sbt-vcpkg-native" % VcpkgVersion)
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

libraryDependencySchemes ++= Seq(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always
)

addSbtPlugin("com.github.lolgab" % "sbt-snunit" % "0.13.0")
