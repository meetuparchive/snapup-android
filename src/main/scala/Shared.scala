package snapup

import android.content.{SharedPreferences, Context, Intent}
import android.util.Log

import dispatch._
import dispatch.meetup._
import dispatch.oauth._
import Request._

trait PrefEditing {
  implicit def sp2editing(sp: SharedPreferences) = new EditingContext(sp)
  class EditingContext(sp: SharedPreferences) {
    def editor(block: SharedPreferences.Editor => Unit) = {
      val editor = sp.edit()
      block(editor)
      editor.commit()
    }
  }
}

class Prefs(context: Context) extends PrefEditing {
  val request = context.getSharedPreferences("request", Context.MODE_PRIVATE)
  val access = context.getSharedPreferences("access", Context.MODE_PRIVATE)
  val meetups = context.getSharedPreferences("meetups", Context.MODE_PRIVATE)
  private val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
  def today = df.format(new java.util.Date)
  def clear() {
    (access :: request :: meetups :: Nil) foreach { p =>
      p.editor { _.clear() }
    }
  }
}

object Account {
  import scala.collection.JavaConversions._
  def tokens(sp: SharedPreferences) = Token(sp.getAll.asInstanceOf[java.util.Map[String,Any]])
  val consumer = Consumer("72DA10F33DB36B11DA502251ED135E76","F6805ED5DB63D7AFE9BF0506B6430CF2")
  def client(access: Token) = OAuthClient(consumer, access)
  def client(prefs: Prefs) = tokens(prefs.access) map { access => OAuthClient(consumer, access) }
}
class AndroidHttp extends Http {
  override lazy val log = new Logger {
    def info(msg: String, items: Any*) { 
      Log.i("Main", "INF: [android logger] dispatch: " + msg.format(items: _*)) 
    }
  }
}
trait Cache[T] {
  import java.lang.ref.SoftReference
  private var cache = Map.empty[String, SoftReference[T]]
  def get(key: String) =
    if (cache.contains(key)) Option(cache(key).get)
    else None
  def put(key: String)(item: T) = synchronized {
    cache = cache + ((key, new SoftReference(item)))
    item
  }
}
class HttpCache[T] extends Cache[T] {
  import dispatch.futures.DefaultFuture.future
  def load(retrieve: String => Handler[T])(key: String)(use: T => Unit) { get(key) match {
    case Some(item) => use(item)
    case None => future { (new AndroidHttp)(retrieve(key) ~> put(key) ~> use) }
  } }
}
object ImageCache extends HttpCache[android.graphics.Bitmap] {
  def apply(url: String) = load { _ >> { stm => android.graphics.BitmapFactory.decodeStream(stm) } } (url) _
  val large_r = "/(member|global)_".r
  def thumb(url: String) = apply(large_r.replaceFirstIn(url, "/thumb_"))
}
