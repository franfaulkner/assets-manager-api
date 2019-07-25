name := "assets-manager-api"

version := "0.1"

organization := "com.thingso2"

val registry = "repo.thingso2.com:5000"


publishTo := version { v: String =>
  val nexus = "https://repo.thingso2.com/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "repository/maven-snapshots")
  else
    Some("releases" at nexus + "repository/maven-releases")
}.value

resolvers ++= Seq(
  "ThingsO2" at "https://repo.thingso2.com/repository/maven/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= Seq(
  "com.thingso2" %% "agent-commons" % "0.5.0-SNAPSHOT"
   exclude("javax.ws.rs", "javax.ws.rs-api")
   exclude("org.glassfish.jersey.containers", "jersey-container-servlet")
   exclude("com.google.guava", "guava"),
  "com.thingso2" %% "sdkto2" % "0.2.0"
   exclude("com.google.guava", "guava"),
  "com.thingso2" %% "testkit" % "0.2.0"
   exclude("com.google.guava", "guava"),
  "com.typesafe.akka" %% "akka-actor" % "2.5.12",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % Test,
  "org.mockito" % "mockito-all" % "1.10.19" % Test
)

testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a"))

scalaVersion := "2.11.12"

fork in Test := true

// DOCKER
enablePlugins(sbtdocker.DockerPlugin)

dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8u191-jdk-alpine3.9")
    run("apk", "add", "--no-cache", "curl")
    add(artifact, artifactTargetPath)

    val entryPoint: String = "export APPLICATION_SECRET=`dd if=/dev/urandom bs=1 count=64 2>/dev/null | " +
      """base64 | tr -d "\n"| rev | cut -c -64 | rev` && """ +
      "curl -sS ${LOGBACK_URL:=http://consul:8500/v1/kv/pre/services/api/logback.xml?raw} > logback.xml && " +
      "java -Dplay.http.secret.key=$APPLICATION_SECRET " +
      "-Dhttp.port=${HTTP_PORT:=9000} " +
      "-Dhttp.address=${HTTP_ADDRESS:=0.0.0.0} " +
      "-Dconfig.url=${CONFIG_URL} " +
      "-Dlogger.file=logback.xml " +
      s"-jar $artifactTargetPath"
    entryPointRaw(entryPoint)
  }
}

imageNames in docker := Seq(
  ImageName(
    registry = Option(registry),
    namespace = Option(organization.value),
    repository = name.value,
    tag = Some(version.value)
  )
)

assemblyMergeStrategy in assembly := {
  // Building fat jar without META-INF
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case PathList("reference-overrides.conf") => MergeStrategy.concat
  case PathList("logback.xml") => MergeStrategy.last
  // Take last config file
  case PathList(ps@_*) if ps.last endsWith ".conf" => MergeStrategy.concat
  case PathList(ps@_*) if ps.last endsWith ".class" => MergeStrategy.first
  case o =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(o)
}
 
 import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._


    val dockerStep: ReleaseStep = {

      import DockerKeys._
      ReleaseStep(
        action = releaseStepTask(dockerBuildAndPush)
      )
    }

    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      dockerStep,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
