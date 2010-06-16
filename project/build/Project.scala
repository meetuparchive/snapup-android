import sbt._

class SnapupProject(info: ProjectInfo) extends AndroidProject(info: ProjectInfo) 
    with MarketPublish with TypedResources with posterous.Publish {    
  def androidPlatformName="android-1.6"
  def keyalias = "snapup"
  override def keystorePath = Path.userHome / "meetup.keystore"

  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"
  lazy val meetup = "net.databinder" %% "dispatch-meetup" % "0.7.4"

  override def proguardOption = """
    |-keep class dispatch.** { 
    |  public scala.Function1 *();
    |}
    |-keep class snapup.** { 
    |  public scala.Function1 *();
    |}
    |-keep class ** extends dispatch.Builder {
    |  public ** product();
    |}
""".stripMargin
}

trait TypedResources extends AndroidProject {
  import scala.xml._
  val Id = """@\+id/(.*)""".r
  lazy val generateTypedResources = task {
    (mainResPath ** "*.xml").get.foreach { path =>
      val xml = XML.loadFile(path.asFile)
      xml.descendant flatMap { node =>
        node.attributes.get("http://schemas.android.com/apk/res/android", node, "id") flatMap {
          _.firstOption map { _.text } flatMap {
            case Id(id) => try {
              Some("android.widget." + node.label, id)
            } catch { case _ => None }
            case _ => None
          }
        }
      } foreach { n => println(n) }
    }
    None
  }
}