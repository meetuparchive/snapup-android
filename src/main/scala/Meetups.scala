package snapup

import com.meetup.snapup.{R,TR,TypedResource}

import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{ArrayAdapter, ListView, Toast, EditText, TextView, ImageView}
import android.view.{View, ViewGroup, Menu, MenuItem, KeyEvent}
import android.net.Uri
import android.provider.MediaStore
import android.content.{Intent, DialogInterface}
import android.util.Log

import dispatch.meetup._
import dispatch.Http
import dispatch.Http._
import java.io.{File,FileOutputStream}

import net.liftweb.json._
import net.liftweb.json.JsonAST._

import TypedResource._

class Meetups extends ListActivity with ScalaActivity {
  lazy val prefs = new Prefs(this)
  val http = AndroidHttp

  lazy val meetup_json = prefs.meetups.getString(prefs.today, "")
  lazy val meetups =  Response.results(JsonParser.parse(meetup_json)).toArray
  
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    if (meetup_json == "") {
      startActivity(new Intent(Meetups.this, classOf[Main]))
      finish()
    } else {
      case class Row(event: TextView, group: TextView, image: ImageView)
      setListAdapter(new ArrayReusingAdapter[JValue, Row](this, R.layout.row, meetups) {
        def inflateView = View.inflate(Meetups.this, R.layout.row, null)
        def setupRow(view: View) = Row(view.findView(TR.event_name), view.findView(TR.group_name), view.findView(TR.icon))
        def draw(position: Int, row: Row, current: => Boolean) {
          val meetup = meetups(position)
          Event.name(meetup).foreach(row.event.setText)
          Event.group_name(meetup).foreach(row.group.setText)
          Event.photo_url(meetup).foreach { url => ImageCache.thumb(url) { bitmap =>
            post { if (current) row.image.setImageBitmap(bitmap) }
          } }
        }
      })
    }
  }
  
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    val meetup = meetups(position)
    for {
      event_id <- Event.id(meetup)
      event_name <- Event.name(meetup)
      event_time <- Event.time(meetup)
      group_name <- Event.group_name(meetup)
    } {
      startActivity(new Intent(Meetups.this, classOf[Members])
        .putExtra("event_id", event_id)
        .putExtra("event_name", event_name)
        .putExtra("group_name", group_name)
        .putExtra("event_time", event_time)
      )
    }
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.main_menu, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case R.id.menu_item_reset => 
      prefs.clear()
      startActivity(new Intent(Meetups.this, classOf[Main]))
      finish()
      true
    case _  => false
  }
}