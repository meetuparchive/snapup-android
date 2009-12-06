package meetup.example
 
import android.app.ListActivity
import android.os.Bundle
import android.widget.SimpleAdapter
import android.net.Uri
import android.content.{SharedPreferences, Context, Intent}

import dispatch._
import oauth._
import meetup._
import dispatch.liftjson.Js._
import Http._
import net.liftweb.json._
import net.liftweb.json.JsonAST._
 
class MainActivity extends ListActivity {
  implicit val http = new Http
  val consumer = Consumer("72DA10F33DB36B11DA502251ED135E76","F6805ED5DB63D7AFE9BF0506B6430CF2")
  lazy val request_prefs = getSharedPreferences("request", Context.MODE_PRIVATE)
  lazy val access_prefs = getSharedPreferences("access", Context.MODE_PRIVATE)

  def token(sp: SharedPreferences) = Token(
    new scala.collection.jcl.MapWrapper[String, Any] { def underlying = sp.getAll.asInstanceOf[java.util.Map[String,Any]] }
  )
  def write(sp: SharedPreferences, token: Token) = {
    val editor = sp.edit()
    editor.putString("oauth_token", token.value)
    editor.putString("oauth_token_secret", token.secret)
    editor.commit()
    token
  }
  
  def authorize(rt: Token) {
    val intent = new Intent(Intent.ACTION_VIEW)
    intent.setData(Uri.parse(Auth.authorize_url(rt, "snapup:///").to_uri.toString))
    startActivity(intent)
  }
  
  def fetch_meetups(at: Token) {
    val cli = OAuthClient(consumer, at)
    val (events, _) = cli.call(Events.member_id("7230113"))
    val data = for (e <- events; name <- Event.name(e); group <- Event.group_name(e)) yield {
      val map = new java.util.HashMap[String, String]
      map.put("name", name)
      map.put("group", group)
      map
    }

    setListAdapter(new SimpleAdapter(this, 
      java.util.Arrays.asList(data.toArray: _*),
      android.R.layout.simple_list_item_2,
      Array("name", "group"),
      Array(android.R.id.text1, android.R.id.text2)
    ))
  }
  
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    
    token(access_prefs) match {
      case None => 
        getIntent.getData match {
          case null =>
            authorize(write(request_prefs, http(Auth.request_token(consumer))))
          case uri => 
            token(request_prefs) filter { 
              _.value == uri.getQueryParameter("oauth_token") 
            } foreach { rt =>
              fetch_meetups(write(access_prefs, http(Auth.access_token(consumer, rt))))
            }
        }
      case Some(at) => fetch_meetups(at)
    }
  }
}