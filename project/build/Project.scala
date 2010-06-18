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
  def managedScalaPath = "src_managed" / "main" / "scala"
  def typedResource = managedScalaPath / "TR.scala"
  abstract override def mainSourceRoots = super.mainSourceRoots +++ managedScalaPath
  def xmlResources = mainResPath ** "*.xml"
  override def compileAction = super.compileAction dependsOn generateTypedResources
  override def cleanAction = super.cleanAction dependsOn cleanTask(managedScalaPath)
  override def watchPaths = super.watchPaths +++ xmlResources
  
  lazy val generateTypedResources = fileTask(typedResource from xmlResources) {
    val Id = """@\+id/(.*)""".r
    val resources = xmlResources.get.flatMap { path =>
      val xml = XML.loadFile(path.asFile)
      xml.descendant flatMap { node =>
        // all nodes
        node.attribute("http://schemas.android.com/apk/res/android", "id") flatMap {
          // with android:id attribute
          _.firstOption map { _.text } flatMap {
            case Id(id) => try { Some(id,
              // whre ids start with @+id/
              ClasspathUtilities.toLoader(androidJarPath).loadClass(
                // where the label is a widget in the android jar
                "android.widget." + node.label
              ).getName)
            } catch { case _ => None }
            case _ => None
          }
        }
      }
    }.foldLeft(Map.empty[String, String]) { case (m, (k, v)) => m + (k -> v) }
    FileUtilities.write(typedResource.asFile,
    """     |package %s
            |import android.app.Activity
            |import android.view.View
            |
            |class TypedView(view: View) {
            |  def findView[T](tr: TypedResource[T]): T = view.findViewById(tr.id).asInstanceOf[T]
            |}
            |class TypedActivity(activity: Activity) {
            |  def findView[T](tr: TypedResource[T]): T = activity.findViewById(tr.id).asInstanceOf[T]
            |}
            |object TypedResource {
            |  implicit def view2typed(view: View) = new TypedView(view)
            |  implicit def view2typed(activity: Activity) = new TypedActivity(activity)
            |}
            |case class TypedResource[T](id: Int)
            |object TR {
            |%s
            |}""".stripMargin.format(
              manifestPackage, resources map { case (id, classname) =>
                "  val %s = TypedResource[%s](R.id.%s)".format(id, classname, id)
              } mkString "\n"
            ), log
    )
    None
  }
}