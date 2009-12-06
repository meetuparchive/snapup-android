package meetup.example

import android.os.Bundle
import android.view.Window
import android.app.Activity
import android.net.Uri
import android.content.{SharedPreferences, Context, Intent}

import scala.actors.Actor._

import dispatch._
import oauth._
import meetup._
import dispatch.liftjson.Js._
import Http._
import net.liftweb.json._
import net.liftweb.json.JsonAST._

class Prefs(context: Context) {
  val request = context.getSharedPreferences("request", Context.MODE_PRIVATE)
  val access = context.getSharedPreferences("access", Context.MODE_PRIVATE)
}

object Account {
  def tokens(sp: SharedPreferences) = Token(
    new scala.collection.jcl.MapWrapper[String, Any] { def underlying = sp.getAll.asInstanceOf[java.util.Map[String,Any]] }
  )
  val consumer = Consumer("72DA10F33DB36B11DA502251ED135E76","F6805ED5DB63D7AFE9BF0506B6430CF2")
  def client(access: Token) = OAuthClient(consumer, access)
  def client(prefs: Prefs) = tokens(prefs.access) map { access => OAuthClient(consumer, access) }
}

class Main extends Activity {
  implicit val http = new Http
  lazy val prefs = new Prefs(this)

  def write(sp: SharedPreferences, token: Token) = {
    val editor = sp.edit()
    editor.putString("oauth_token", token.value)
    editor.putString("oauth_token_secret", token.secret)
    editor.commit()
    token
  }
  
  def authorize(rt: Token) {
    val intent = new Intent(Intent.ACTION_VIEW)
    intent.setData(Uri.parse((Auth.m_authorize_url(rt) <<? Auth.callback("snapup:///") to_uri).toString))
    startActivity(intent)
  }
  def fetch_meetups(at: Token) {
    val cli = Account.client(at)
    val json =  http(cli(Events.member_id("7230113")) as_str)
    startActivity(new Intent(Main.this, classOf[Meetups]).putExtra("meetups", json))
  }
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main);
  }
  override def onResume() {
    super.onResume()
    actor {
      Account.tokens(prefs.access) match {
        case None => 
          getIntent.getData match {
            case null =>
              authorize(write(prefs.request, http(Auth.request_token(Account.consumer))))
            case uri => 
              Account.tokens(prefs.request) filter { 
                _.value == uri.getQueryParameter("oauth_token") 
              } foreach { rt =>
                fetch_meetups(write(prefs.access, http(Auth.access_token(Account.consumer, rt))))
              }
          }
        case Some(at) => fetch_meetups(at)
      }
    }
  }
}