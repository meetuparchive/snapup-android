package meetup.example
 
import android.app.ListActivity
import android.os.Bundle
import android.widget.SimpleAdapter
import android.view.{Menu, MenuItem}

import dispatch.meetup._

import net.liftweb.json._
import net.liftweb.json.JsonAST._
 
class Meetups extends ListActivity {
  lazy val prefs = new Prefs(this)
  
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
  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.main_menu, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.menu_item_reset => 
      (prefs.access.edit :: prefs.request.edit :: Nil) foreach { p =>
        p.clear()
        p.commit()
      }
      finish()
      true
    case _  => false
  }
}