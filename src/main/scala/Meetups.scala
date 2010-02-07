package snapup

import com.meetup.snapup.R

import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{ArrayAdapter, ListView, Toast, EditText, ImageView}
import android.view.{View, ViewGroup, Menu, MenuItem}
import android.net.Uri
import android.provider.MediaStore
import android.content.{Intent, DialogInterface}
import android.graphics.{BitmapFactory,Bitmap}
import android.util.Log

import dispatch.meetup._
import dispatch.Http
import dispatch.Http._
import java.io.{File,FileOutputStream}

import net.liftweb.json._
import net.liftweb.json.JsonAST._

class Meetups extends ListActivity with ScalaActivity {
  lazy val prefs = new Prefs(this)
  val http = AndroidHttp

  lazy val meetups = Response.results(
    JsonParser.parse(getIntent.getExtras.getString("meetups"))
  ).toArray
  
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setListAdapter(new ArrayAdapter(this, R.layout.row, meetups) {
      override def getView(position: Int, convertView: View, parent: ViewGroup) = {
        val row = View.inflate(Meetups.this, R.layout.row, null)
        val row_text = text_in(row)_
        val meetup = meetups(position)
        Event.name(meetup).foreach(row_text(R.id.event_name))
        Event.group_name(meetup).foreach(row_text(R.id.group_name))
        Event.photo_url(meetup).foreach { url =>
          row.findViewById(R.id.icon) match {
            case view: ImageView => http.future(url >> { is =>
              val bitmap = BitmapFactory.decodeStream(is)
              post { view.setImageBitmap(bitmap) }
            })
          }
        }
        row
      }
    })
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
      (prefs.access.edit :: prefs.request.edit :: Nil) foreach { p =>
        p.clear()
        p.commit()
      }
      finish()
      true
    case _  => false
  }
}