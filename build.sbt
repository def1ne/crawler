name := "crawler"
version := "0.1"
scalaVersion := "2.13.1"

lazy val akkaVersion = "2.5.26"
lazy val akkaHttpVersion = "10.1.10"


libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "org.jsoup" % "jsoup" % "1.11.3",

  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)
