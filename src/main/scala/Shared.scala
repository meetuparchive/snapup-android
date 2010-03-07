package snapup

import android.content.{SharedPreferences, Context, Intent}
import android.util.Log

import dispatch._
import dispatch.meetup._
import dispatch.oauth._
import dispatch.Http._

class Prefs(context: Context) {
  val request = context.getSharedPreferences("request", Context.MODE_PRIVATE)
  val access = context.getSharedPreferences("access", Context.MODE_PRIVATE)
  val meetups = context.getSharedPreferences("meetups", Context.MODE_PRIVATE)
  private val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
  def today = df.format(new java.util.Date)
}

object Account {
  import scala.collection.JavaConversions._
  def tokens(sp: SharedPreferences) = Token(sp.getAll.asInstanceOf[java.util.Map[String,Any]])
  val consumer = Consumer("72DA10F33DB36B11DA502251ED135E76","F6805ED5DB63D7AFE9BF0506B6430CF2")
  def client(access: Token) = OAuthClient(consumer, access)
  def client(prefs: Prefs) = tokens(prefs.access) map { access => OAuthClient(consumer, access) }
}
object AndroidHttp extends Http with Threads {
  override lazy val log = new Logger {
    def info(msg: String, items: Any*) { 
      Log.i("Main", "INF: [android logger] dispatch: " + msg.format(items: _*)) 
    }
  }
}
object ImageCache {
  import java.lang.ref.SoftReference
  import android.graphics.{BitmapFactory,Bitmap}
  private var cache = Map.empty[String, SoftReference[Bitmap]]
  def get(url: String) =
    if (cache.contains(url)) Option(cache(url).get)
    else None
  def put(url: String, bitmap: Bitmap) = synchronized {
    cache = cache + ((url, new SoftReference(bitmap)))
    bitmap
  }
  def use(url: String)(block: Bitmap => Unit) { get(url) match {
    case Some(bitmap) => block(bitmap)
    case None => 
      AndroidHttp.future(url >> { stm =>
        block(put(url, BitmapFactory.decodeStream(stm)))
      })
  } }
}