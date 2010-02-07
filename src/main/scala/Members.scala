package snapup

import com.meetup.snapup.R

import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{ArrayAdapter, ListView, Toast, EditText, TextView, ImageView}
import android.view.{View, ViewGroup, Menu, MenuItem}
import android.net.Uri
import android.provider.MediaStore
import android.content.{Intent, DialogInterface}
import android.graphics.{BitmapFactory,Bitmap}
import android.util.Log

import dispatch.meetup._
import dispatch.Http
import dispatch.Http._
import dispatch.liftjson.Js._
import java.io.{File,FileOutputStream}

import net.liftweb.json._
import net.liftweb.json.JsonAST._

class Members extends ListActivity with ScalaActivity {
  lazy val prefs = new Prefs(this)
  val http = AndroidHttp

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.members)
    val Some(cli) = Account.client(prefs)
    def extras(str: String) = getIntent.getExtras.getString(str)
    text_in(this)(R.id.event_name)(extras("event_name"))
    text_in(this)(R.id.group_name)(extras("group_name"))
    text_in(this)(R.id.event_time)(extras("event_time"))
    http.future(cli(Rsvps.event_id(extras("event_id"))) ># { json =>
      val rsvps = Response.results(json).filter { rsvp =>
        Rsvp.response(rsvp) exists { r => r == "yes" || r == "maybe" }
      }.toArray
      post { setListAdapter(new ArrayAdapter(this, R.layout.row, rsvps) {
        override def getView(position: Int, convertView: View, parent: ViewGroup) = {
          val row = View.inflate(Members.this, R.layout.row, null)
          val rsvp = rsvps(position)
          Rsvp.name(rsvp).foreach(text_in(row)(R.id.event_name))
          Rsvp.response(rsvp).filter { _ == "maybe" }.foreach(text_in(row)(R.id.group_name))
          Rsvp.photo_url(rsvp).foreach { url =>
            row.findViewById(R.id.icon) match {
              case view: ImageView => http.future(url >> { is =>
                val bitmap = BitmapFactory.decodeStream(is)
                post { view.setImageBitmap(bitmap) }
              })
            }
          }
          row
        }
      })}
    })
    
  }
}