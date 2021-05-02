lazy val root = (project in file(".")).settings(
  name := "playground",
  scalaVersion := "2.13.1",
  scalafmtOnCompile := true,
  scalacOptions -= "-Xfatal-warnings", // enable all options from sbt-tpolecat except fatal warnings
  initialCommands := s"import Playground._",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
  ),
  libraryDependencies ++= dependencies,
  libraryDependencies +=
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
)

def dep(org: String, prefix: String, version: String)(modules: String*) =
  modules.map(m => org %% (prefix ++ m) % version)

lazy val dependencies = {
  val fs2 = dep("co.fs2", "fs2-", "3.0.2")("core", "io")

  val http4s = dep("org.http4s", "http4s-", "1.0.0-M21")(
    "dsl",
    "blaze-server",
    "blaze-client",
    "circe",
    "scala-xml"
  )

  val circe = dep("io.circe", "circe-", "0.13.0")(
    "generic",
    "literal",
    "parser"
  )

  val mixed = Seq(
    "org.typelevel" %% "cats-free" % "2.1.0",
    "org.typelevel" %% "kittens" % "2.2.2",
    "com.chuusai" %% "shapeless" % "2.3.4"
  )

  fs2 ++ http4s ++ circe ++ mixed
}
