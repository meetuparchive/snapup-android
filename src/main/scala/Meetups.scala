package meetup.example
 
import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{SimpleAdapter, ListView, Toast, EditText}
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
  val clean_on_cancel = new DialogInterface.OnCancelListener {
    def onCancel(dialog: DialogInterface) { image_f.delete() }
  }
  val clean_on_click = new DialogInterface.OnClickListener {
    def onClick(dialog: DialogInterface, which: Int) { image_f.delete() }
  }
  
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image_f)), position)
  }
  override def onActivityResult(index: Int, result: Int, intent: Intent) {
    result match {
      case Activity.RESULT_OK =>
        val m = meetups(index)
        val event_name = Event.name(m).head
        val event_id = Event.id(m).head
        // insert as bitmap so gallery will own the file
        MediaStore.Images.Media.insertImage(
          getContentResolver(), BitmapFactory.decodeFile(image_f.getPath), 
          event_name, Event.group_name(m).head
        )
        get_caption(event_name) { caption =>
          try_upload(event_id, caption)
        }
      case _ => Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show()
    }
  }
  
  def try_upload(event_id: String, caption: String) {
    val loading = loading_dialog
    actor {
      def dismiss = handler.sendMessage(Message.obtain(handler, DISMISS, loading))
      try {
        Account.client(prefs) orElse { 
          error("somehow in Meetups#try_upload() without valid client")
        } foreach { cli =>
          cli.call(PhotoUpload.event_id(event_id).caption(caption).photo(image_f))
        }
        image_f.delete()
        dismiss
      } catch {
        case e => 
          Log.e("Meetups", "Error uploading photo", e)
          dismiss
          handler.sendMessage(Message.obtain(handler, FAILED, (event_id, caption)))
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
      case (FAILED, (event_id: String, caption: String)) => 
        failed_dialog(event_id, caption)
        true
      case _ => false
    }
  })
  
  def get_caption[T](event_name: String)(block: String => Unit) {
    val input = new EditText(this)
    val dialog = new AlertDialog.Builder(this)
      .setTitle(event_name)
      .setMessage("Photo Caption: (optional)")
      .setPositiveButton("Post", new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, button: Int) {
          block(input.getText.toString)
        }
      }).setNegativeButton("Cancel", clean_on_click)
      .setOnCancelListener(clean_on_cancel)
      .create()
    dialog.setView(input, 15, 15, 15, 15)
    dialog.show()
  }

  def loading_dialog = ProgressDialog.show(Meetups.this, "", "Posting photo to Meetup", true)
  
  def failed_dialog(event_id: String, caption: String) {
    new AlertDialog.Builder(this)
      .setTitle(R.string.upload_failed_title)
      .setMessage(R.string.upload_failed)
      .setPositiveButton("Retry", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, which: Int) { try_upload(event_id, caption) }
      })
      .setNeutralButton("Okay", clean_on_click)
      .setOnCancelListener(clean_on_cancel)
      .create()
      .show()
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