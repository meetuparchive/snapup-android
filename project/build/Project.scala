import sbt._

class SnapupProject(info: ProjectInfo) extends AndroidProject(info: ProjectInfo) with MarketPublish {    
  def androidPlatformName="android-1.6"
  def key_alias = "mykey"

  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"
  lazy val meetup = "net.databinder" %% "dispatch-meetup" % "0.7.0-beta1"

  override def proguardOption = """
    |-keep class dispatch.** { 
    |  public scala.Function1 *();
    |}
    |-keep class ** extends dispatch.Builder {
    |  public ** product();
    |}
""".stripMargin
}