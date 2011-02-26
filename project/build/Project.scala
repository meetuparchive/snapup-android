import sbt._

class SnapupProject(info: ProjectInfo) extends AndroidProject(info: ProjectInfo) 
    with MarketPublish with TypedResources with posterous.Publish {    
  def androidPlatformName="android-4"
  def keyalias = "snapup"
  override def keystorePath = Path.userHome / "meetup.keystore"

  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"
  lazy val dmeet = "net.databinder" %% "dispatch-meetup" % "0.8.0.Beta3"
  lazy val dnio = "net.databinder" %% "dispatch-nio" % "0.8.0.Beta3"

  override def proguardOption = """
    |-keep class dispatch.** { 
    |  public scala.Function1 *();
    |}
    |-keep class org.apache.http.protocol.ImmutableHttpProcessor { 
    |  public ** *();
    |}
    |-keep class snapup.** { 
    |  public scala.Function1 *();
    |}
    |-keep class ** extends dispatch.Builder {
    |  public ** product();
    |}
""".stripMargin

  override def ivyXML =
    <dependencies>
       <exclude module="commons-logging" conf="compile"/>              
       <exclude module="commons-codec" conf="compile"/>      
       <exclude module="scala-library" conf="compile"/>                    
    </dependencies>
}
