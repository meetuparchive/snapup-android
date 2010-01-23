import sbt._

class SnapupProject(info: ProjectInfo) extends AndroidProject(info: ProjectInfo) {    
  override def androidSdkPath = Path.fromFile("/usr/local/android-sdk-mac/")
  override def androidPlatformName="android-1.6"

  val databinder_net = "databinder.net repository" at "http://databinder.net/repo"
  lazy val meetup = "net.databinder" %% "dispatch-meetup" % "0.6.7-SNAPSHOT"
  override def proguardOption = """
    |-keep class dispatch.** { 
    |  public scala.Function1 *();
    |}
    |-keep class ** extends dispatch.Builder {
    |  public ** product();
    |}
""".stripMargin

  import Process._
  def keystore = Path.userHome / "android-market.keystore"
  def key_alias = "mykey"
  lazy val signRelease = signReleaseAction
  def signReleaseAction = execTask {<x>
      jarsigner -verbose -keystore {keystore} -storepass {getPassword} {packageApkPath} {key_alias}
  </x>} dependsOn(packageRelease) describedAs("Sign with private key, using jarsigner.")
  def getPassword = SimpleReader.readLine("\nEnter keystore password: ").get
  
  def packageAlignedName = artifactBaseName + "-aligned" + ".apk"
  def packageAlignedPath = outputPath / packageAlignedName
  
  def zipAlignPath = androidToolsPath / "zipalign"
  
  lazy val alignRelease = alignReleaseAction
  def alignReleaseAction = execTask {<x>
      {zipAlignPath} -v  4 {packageApkPath} {packageAlignedPath}
  </x>} dependsOn(signReleaseAction) describedAs("Run zipalign on signed jar.")
}