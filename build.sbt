name := """play-silhouette-credentials-seed"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-language:reflectiveCalls", "-language:postfixOps", "-language:implicitConversions")

resolvers ++= Seq(
  "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  Resolver.jcenterRepo
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

pipelineStages := Seq(rjs, digest, gzip)

RjsKeys.mainModule := "main"

doc in Compile <<= target.map(_ / "none")

scalariformSettings

proguardSettings

ProguardKeys.options in Proguard ++= Seq("-dontnote", "-dontwarn", "-ignorewarnings")
ProguardKeys.proguardVersion in Proguard := "5.2.1"
val keepClasses =
  """
    |-keepparameternames
    |-keepattributes Exceptions,InnerClasses,Signature,Deprecated,
    |                SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
    |
    |-keep,includedescriptorclasses interface com.** {
    |    <methods>;
    |}
  """.stripMargin
ProguardKeys.options in Proguard += keepClasses
javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")

libraryDependencies ++= Seq(
  cache,
  ws,
  specs2 % Test,
  "org.webjars" % "requirejs" % "2.3.1",
  "com.adrianhurt" %% "play-bootstrap" % "1.1-P25-B3",	// Add bootstrap helpers and field constructors (http://adrianhurt.github.io/play-bootstrap/)
  "com.mohiva" %% "play-silhouette" % "4.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "4.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "4.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "4.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "4.0.0" % "test",
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "com.iheart" %% "ficus" % "1.2.6",
  "com.typesafe.play" %% "play-mailer" % "5.0.0",
  "com.typesafe.slick" %% "slick" % "3.1.1" withSources() withJavadoc(),
  "com.github.tminglei" %% "slick-pg_circe-json" % "0.14.1",
  "com.github.tototoshi" %% "slick-joda-mapper" % "2.0.0",
  "com.h2database" % "h2" % "1.4.190",
  "com.roundeights" %% "hasher" % "1.2.0"
)