lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(
    name := """HelloPlayWebSample""",
    organization := "com.hibiup",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.12.6",
    libraryDependencies ++= Seq(
        guice,
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
    )
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.hibiup.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.hibiup.binders._"
