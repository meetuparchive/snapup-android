package meetup.example
 
import android.app.{Activity, ListActivity}
import android.os.{Bundle, Environment}
import android.widget.{SimpleAdapter, ListView, Toast}
import android.view.{View, Menu, MenuItem}
import android.net.Uri
import android.provider.MediaStore
import android.content.Intent

import dispatch.meetup._
import dispatch.Http
import java.io.File

import net.liftweb.json._
import net.liftweb.json.JsonAST._
 
class Meetups extends ListActivity {
  lazy val prefs = new Prefs(this)
  implicit val http = new Http
  
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
  val file = new File(Environment.getExternalStorageDirectory, "snapup.jpg")
  
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file)), 1)
  }
  
  override def onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
    resultCode match {
      case Activity.RESULT_OK =>
        MediaStore.Images.Media.insertImage(getContentResolver(), file.getPath, "Snapup", "Snapup Photo")
        Account.client(prefs) foreach { cli =>
          cli.call(PhotoUpload.event_id(12345).photo(file))
        }
      case _ => Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show()
    }
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