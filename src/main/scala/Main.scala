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

class Main extends ScalaActivity {
  val http = AndroidHttp.on_error {
    case StatusCode(400, _) =>
      prefs.clear()
      retrieve_tokens()
    case e => post {
      Log.e("Main", "Error Authenticating with Meetup", e)
      auth_dialog.dismiss()
      def exit = finish()
      new AlertDialog.Builder(Main.this)
        .setTitle("Connection Error")
        .setMessage("Snapup requires a network connection to retrieve your Meetups.")
        .setPositiveButton("Exit", () => exit)
        .setOnCancelListener(() => exit)
        .show()
    }
  }
  lazy val prefs = new Prefs(this)

  def write(sp: SharedPreferences, token: Token) = {
    sp.editor { e =>
      e.putString("oauth_token", token.value)
      e.putString("oauth_token_secret", token.secret)
    }
    token
  }
  
  def authorize(rt: Token) {
    val intent = new Intent(Intent.ACTION_VIEW)
    intent.setData(Uri.parse(Auth.m_authorize_url(rt).to_uri.toString))
    startActivity(intent)
    auth_dialog.dismiss()
    finish()
  }
  def fetch_meetups(at: Token) {
    def proceed() = {
      startActivity(new Intent(Main.this, classOf[Meetups]))
      auth_dialog.dismiss()
      Main.this.finish()
    }
    if (prefs.meetups.contains(prefs.today)) proceed()
    else  {
      prefs.meetups.editor { _.clear() }
      val cli = Account.client(at)
      http.future(cli(Members.self) ># { json =>
        val List(id) = Response.results(json) >>= Member.id
        http(cli(Events.member_id(id).status(Event.Past, Event.Upcoming)) >- { json =>
          post { 
            prefs.meetups.editor { e =>
              e.clear()
              e.putString(prefs.today, json)
            }
            proceed() 
          }
        })
      })
    }
  }
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    auth_dialog
  }
  lazy val auth_dialog = ProgressDialog.show(this, "", "Authenticating with Meetup", true)
  override def onResume() {
    super.onResume()
    retrieve_tokens()
  }
  def retrieve_tokens() {
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