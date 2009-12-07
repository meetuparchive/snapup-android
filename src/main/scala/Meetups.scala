package meetup.example
 
import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{SimpleAdapter, ListView, Toast}
import android.view.{View, Menu, MenuItem}
import android.net.Uri
import android.provider.MediaStore
import android.content.{Intent, DialogInterface}
import android.graphics.BitmapFactory
import android.util.Log

import dispatch.meetup._
import dispatch.Http
import java.io.File
import scala.actors.Actor._

import net.liftweb.json._
import net.liftweb.json.JsonAST._
 
class Meetups extends ListActivity {
  lazy val prefs = new Prefs(this)
  implicit val http = new Http
  lazy val meetups =
    Response.results(JsonParser.parse(getIntent.getExtras.getString("meetups")))
  
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
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
    actor { handler.sendEmptyMessage(0) }
  }
  val image_f = new File(Environment.getExternalStorageDirectory, "snapup-temp.jpg")
  
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image_f)), position)
  }
  override def onActivityResult(index: Int, result: Int, intent: Intent) {
    result match {
      case Activity.RESULT_OK =>
        val m = meetups(index)
        // insert as bitmap so gallery will own the file
        MediaStore.Images.Media.insertImage(
          getContentResolver(), BitmapFactory.decodeFile(image_f.getPath), 
          Event.name(m).head, Event.group_name(m).head
        )
        try_upload(index)
      case _ => Toast.makeText(this, R.string.post_cancelled, Toast.LENGTH_SHORT).show()
    }
  }
  
  def try_upload(index: Int) {
    val loading = loading_dialog
    actor {
      try {
        Account.client(prefs) orElse { 
          error("somehow in Meetups#try_upload() without valid client")
        } foreach { cli =>
          cli.call(PhotoUpload.event_id(Event.id(meetups(index)).head).photo(image_f))
        }
        image_f.delete()
        handler.sendMessage(Message.obtain(handler, DISMISS, loading))
      } catch {
        case e => 
          Log.e("Meetups", "Error uploading photo", e)
          handler.sendMessage(Message.obtain(handler, DISMISS, loading))
          handler.sendMessage(Message.obtain(handler, FAILED, index))
      }
    }
  }
  val DISMISS = 1
  val FAILED = 0
  val handler = new Handler(new Handler.Callback {
    def handleMessage(message: Message) = (message.what, message.obj) match {
      case (DISMISS, dialog: ProgressDialog) => 
        dialog.dismiss()
        true
      case (FAILED, event_index: Integer) => 
        failed_dialog(event_index.intValue)
        true
      case _ => false
    }
  })
  
  def failed_dialog(event_index: Int) {
    new AlertDialog.Builder(this)
      .setTitle(R.string.upload_failed_title)
      .setMessage(R.string.upload_failed)
      .setPositiveButton("Retry", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, which: Int) { try_upload(event_index) }
      })
      .setNeutralButton("Okay", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, which: Int) { image_f.delete() }
      })
      .setOnCancelListener(new DialogInterface.OnCancelListener {
        def onCancel(dialog: DialogInterface) { image_f.delete() }
      })
      .create()
      .show()
  }
  
  def loading_dialog = ProgressDialog.show(Meetups.this, "", "Posting photo to Meetup", true)

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