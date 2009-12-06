package meetup.example
 
import android.app.ListActivity
import android.os.Bundle
import android.widget.SimpleAdapter

import dispatch.meetup._

import net.liftweb.json._
import net.liftweb.json.JsonAST._
 
class Meetups extends ListActivity {
  
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val js = JsonParser.parse(getIntent.getExtras.getString("meetups"))
    val meetups = Response.results(js)
    val data = for (m <- meetups; name <- Event.name(m); group <- Event.group_name(m)) yield {
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