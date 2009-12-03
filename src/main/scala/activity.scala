package com.meetup
 
import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.widget.TextView
 
class MainActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    import dispatch._
    import oauth._
    import meetup._
    import dispatch.liftjson.Js._
    import Http._
    import net.liftweb.json._
    import net.liftweb.json.JsonAST._
    val consumer = Consumer("72DA10F33DB36B11DA502251ED135E76","F6805ED5DB63D7AFE9BF0506B6430CF2")
    val at = Token("6beed634b161ce2ff6f3963ea6287b9e","3ce7bbbe6a5b200b3117d83e5196b6e9")
    val cli = OAuthClient(consumer, at)
    val h = new Http
    val str = h(cli(Events.member_id("7230113")) ># (
      Response.results andThen { _ flatMap Event.name }
    )) mkString "\n"
    
    setContentView(new TextView(this) {
      setText(str)
    })
  }
}