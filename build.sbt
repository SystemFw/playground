Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "3.8.0"
ThisBuild / libraryDependencies := Seq(
 "org.typelevel" %% "toolkit" % "0.1.29",
 "org.typelevel" %% "toolkit-test" % "0.1.29"
)

// If we don't fork, Dynamo Local stays up when C-c C-c from sbt
ThisBuild / fork := true

ThisBuild / scalacOptions := Seq(
  "-deprecation"

)
