import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val android = "android-plugin" % "android-plugin" % "0.1.3"
}