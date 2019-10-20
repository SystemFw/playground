lazy val root = (project in file(".")).settings(
    name := "playground",
  scalaVersion := "2.12.8",
  scalafmtOnCompile := true,
  scalacOptions -= "-Xfatal-warnings", // enable all options from sbt-tpolecat except fatal warnings
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
  initialCommands := s"import Playground._",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
  ),
  libraryDependencies ++= dependencies
)

def dep(org: String, prefix: String, version: String)(modules: String*) =
  modules.map(m => org %% (prefix ++ m) % version)

lazy val dependencies = {
  val fs2 = dep("co.fs2", "fs2-", "2.0.1")("core", "io")

  val http4s = dep("org.http4s", "http4s-", "0.21.0-M5")(
    "dsl",
    "blaze-server",
    "blaze-client",
    "circe",
    "scala-xml"
  )

  val circe = dep("io.circe", "circe-", "0.11.0")(
    "generic",
    "literal",
    "parser"
  )

  val mixed = Seq(
    "org.typelevel" %% "mouse" % "0.20",
    "org.typelevel" %% "kittens" % "1.2.0",
    "com.chuusai" %% "shapeless" % "2.3.3"
  )

  fs2 ++ http4s ++ circe ++ mixed
}
