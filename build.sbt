val playPac4jVersion = "7.0.1"
val pac4jVersion = "3.6.1"
val playVersion = "2.7.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(
    name := """HelloPlayWebSample""",
    organization := "com.hibiup",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.12.6",
    resolvers ++= Seq(
        "Typesafe" at "http://repo.typesafe.com/typesafe/releases/",
        "shibboleth-releases" at "https://build.shibboleth.net/nexus/content/repositories/releases/"
    ),
    libraryDependencies ++= Seq(
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
        guice,
        "org.pac4j" %% "play-pac4j" % playPac4jVersion,
        "org.pac4j" % "pac4j-http" % pac4jVersion,
        "org.pac4j" % "pac4j-cas" % pac4jVersion,
        "org.pac4j" % "pac4j-openid" % pac4jVersion exclude("xml-apis", "xml-apis"),
        "org.pac4j" % "pac4j-oauth" % pac4jVersion,
        "org.pac4j" % "pac4j-saml" % pac4jVersion,
        "org.pac4j" % "pac4j-oidc" % pac4jVersion exclude("commons-io", "commons-io"),
        "org.pac4j" % "pac4j-gae" % pac4jVersion,
        "org.pac4j" % "pac4j-jwt" % pac4jVersion exclude("commons-io", "commons-io"),
        "org.pac4j" % "pac4j-ldap" % pac4jVersion,
        "org.pac4j" % "pac4j-sql" % pac4jVersion,
        "org.pac4j" % "pac4j-mongo" % pac4jVersion,
        "org.pac4j" % "pac4j-kerberos" % pac4jVersion,
        "org.pac4j" % "pac4j-couch" % pac4jVersion,
        "org.apache.shiro" % "shiro-core" % "1.4.0",
        "com.typesafe.play" % "play-cache_2.12" % playVersion,
        "commons-io" % "commons-io" % "2.5"
    )
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.hibiup.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.hibiup.binders._"
