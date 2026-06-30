val scala3Version = "3.4.2"

val commonSettings = Seq(
  scalaVersion := scala3Version,
  scalaJSLinkerConfig ~= {
    _.withModuleKind(ModuleKind.NoModule)
  },
  scalaJSUseMainModuleInitializer := true,
)

lazy val common = project
  .in(file("common"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    commonSettings,
    name := "common",
  )

lazy val background = project
  .in(file("background"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "background",
  )

lazy val content = project
  .in(file("content"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "content",
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
  )

lazy val buildExtension = taskKey[Unit]("Compile all subprojects and copy JS to extension directory")

buildExtension := {
  val bgReport = (background / Compile / fastLinkJS).value
  val ctReport = (content   / Compile / fastLinkJS).value
  val bgOutDir = (background / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  val ctOutDir = (content   / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  val extDir   = baseDirectory.value.getParentFile
  val bgSrc    = bgReport.data.publicModules.head.jsFileName
  val ctSrc    = ctReport.data.publicModules.head.jsFileName
  IO.copyFile(bgOutDir / bgSrc, extDir / "background.js")
  IO.copyFile(ctOutDir / ctSrc, extDir / "content.js")
  streams.value.log.info(s"Extension JS written to $extDir")
}
