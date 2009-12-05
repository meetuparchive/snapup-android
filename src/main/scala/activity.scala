package meetup.example
 
import android.app.ListActivity
import android.os.Bundle
import android.widget.SimpleAdapter
 
class MainActivity extends ListActivity {
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
    implicit val h = new Http
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
}