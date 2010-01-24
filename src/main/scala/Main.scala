package snapup

import android.os.Bundle
import android.app.{Activity, AlertDialog, ProgressDialog}
import android.net.Uri
import android.content.{SharedPreferences, Context, Intent}
import android.util.Log
import android.content.DialogInterface

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
object AndroidHttp extends Http with Threads {
  override lazy val log = new Logger {
    def info(msg: String, items: Any*) { 
      Log.i("Main", "INF: [android logger] dispatch: " + msg.format(items: _*)) 
    }
  }
}

class Main extends ScalaActivity {
  val http = AndroidHttp.on_error {
    case e => post {
      Log.e("Main", "Error Authenticating with Meetup", e)
      auth_dialog.dismiss()
      new AlertDialog.Builder(Main.this)
        .setTitle("Connection Error")
        .setMessage("Snapup requires a network connection to retrieve your Meetups.")
        .setNeutralButton("Exit", Main.this.finish())
        .setOnCancelListener(Main.this.finish())
        .show()
    }
  }
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
    intent.setData(Uri.parse(Auth.m_authorize_url(rt).to_uri.toString))
    startActivity(intent)
  }
  def fetch_meetups(at: Token) {
    val cli = Account.client(at)
    http.future(cli(Members.self) ># { json =>
      val List(id) = Response.results(json) >>= Member.id
      http(cli(Events.member_id(id).status(Event.Past, Event.Upcoming)) >- { json =>
        startActivity(new Intent(Main.this, classOf[Meetups]).putExtra("meetups", json))
        Main.this.finish()
      })
    })
  }
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    auth_dialog
  }
  lazy val auth_dialog = ProgressDialog.show(this, "", "Authenticating with Meetup", true)
  override def onResume() {
    super.onResume()
    Account.tokens(prefs.access) match {
      case None => 
        getIntent.getData match {
          case null =>
            http.future(Auth.request_token(Account.consumer, "snapup:///") ~> { token =>
              authorize(write(prefs.request, token))
            })
          case uri => 
            Account.tokens(prefs.request) filter { 
              _.value == uri.getQueryParameter("oauth_token") 
            } foreach { rt => http.future(
              Auth.access_token(Account.consumer, rt, uri.getQueryParameter("oauth_verifier")) ~> { token: oauth.Token =>
                fetch_meetups(write(prefs.access, token))
              }
            )
          }
        }
      case Some(at) => fetch_meetups(at)
    }
  }
}