import org.scalajs.linker.interface.ModuleSplitStyle

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "org.acet"
ThisBuild / version      := "3.0.0"

// Dipendenze condivise dalle versioni per evitare conflitti tra i moduli.
val zioVersion      = "2.1.9"
val zioHttpVersion  = "3.0.1"
val tapirVersion    = "1.11.7"
val circeVersion    = "0.14.10"
val quillVersion    = "4.8.5"
val sttpVersion     = "3.9.8"
val laminarVersion  = "17.1.0"

lazy val root = (project in file("."))
  .aggregate(shared.jvm, shared.js, backend, frontend)
  .settings(publish / skip := true)

// --- shared: DTO e definizioni Tapir condivise tra backend (JVM) e frontend (JS) ---
lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/shared"))
  .settings(
    name := "policy-decoder-shared",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %%% "tapir-core"       % tapirVersion,
      "com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % tapirVersion,
      "io.circe"                    %%% "circe-core"       % circeVersion,
      "io.circe"                    %%% "circe-generic"    % circeVersion,
      "io.circe"                    %%% "circe-parser"     % circeVersion,
    ),
  )

// --- backend: server ZIO HTTP + Tapir + Quill (Postgres) ---
lazy val backend = (project in file("modules/backend"))
  .dependsOn(shared.jvm)
  .settings(
    name := "policy-decoder-backend",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"                          % zioVersion,
      "dev.zio" %% "zio-http"                      % zioHttpVersion,
      "dev.zio" %% "zio-json"                      % "0.7.3",
      "com.softwaremill.sttp.tapir"   %% "tapir-zio"            % tapirVersion,
      "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server" % tapirVersion,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion,
      "io.getquill" %% "quill-jdbc-zio" % quillVersion,
      "org.postgresql" % "postgresql"   % "42.7.4",
      "org.flywaydb"   % "flyway-core"          % "10.20.1",
      "org.flywaydb"   % "flyway-database-postgresql" % "10.20.1",
      "com.typesafe"   % "config"               % "1.4.3",
      "org.mindrot"    % "jbcrypt"              % "0.4",
      "com.eatthepath" % "java-otp"             % "0.4.0",
      "commons-codec"  % "commons-codec"        % "1.17.1",
      "com.nimbusds"   % "nimbus-jose-jwt"      % "9.40",
      "net.dankito.readability4j" % "readability4j" % "1.0.8",
      "com.google.zxing" % "core"    % "3.5.3",
      "com.google.zxing" % "javase"  % "3.5.3",
      "ch.qos.logback" % "logback-classic" % "1.5.12",
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Compile / mainClass := Some("decoder.backend.Main"),
    // Fat jar per il Dockerfile (stage di build): `sbt backend/assembly`.
    // Merge strategy minimale ma non compiler-verificata: se l'assembly
    // fallisce per un duplicato non coperto qui, è il primo posto da guardare.
    assembly / mainClass := Some("decoder.backend.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case "module-info.class"      => MergeStrategy.discard
      case "reference.conf"         => MergeStrategy.concat
      case _                        => MergeStrategy.first
    },
  )

// --- frontend: Laminar + ScalaJS, "minimo minimo" per design esplicito richiesto ---
lazy val frontend = (project in file("modules/frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared.js)
  .settings(
    name := "policy-decoder-frontend",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(org.scalajs.linker.interface.ModuleKind.ESModule)
      .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("decoder.frontend")))),
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % laminarVersion,
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.softwaremill.sttp.client3" %%% "core" % sttpVersion,
      "com.softwaremill.sttp.tapir"   %%% "tapir-sttp-client" % tapirVersion,
    ),
  )
