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
import dispatch.mime.Mime._
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
  val image_f = new File(Environment.getExternalStorageDirectory, "snapup-temp.jpg")
  def clean_image() { image_f.delete() }
  
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    val List(event_id) = Event.id(meetups(position))
    val List(event_name) = Event.name(meetups(position))
    startActivity(new Intent(Meetups.this, classOf[Members])
      .putExtra("event_id", event_id)
      .putExtra("event_name", event_name)
    )
//    startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image_f)), position)
  }
  override def onActivityResult(index: Int, result: Int, intent: Intent) {
    result match {
      case Activity.RESULT_OK =>
        val m = meetups(index)
        val event_name = Event.name(m).head
        val event_id = Event.id(m).head
        // insert as bitmap so gallery will own the file
        val bitmap = BitmapFactory.decodeFile(image_f.getPath)
        MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, event_name, Event.group_name(m).head)
        // save with max width for upload
        val max = 800
        if (bitmap.getWidth > max) {
          val smaller = Bitmap.createScaledBitmap(bitmap, max,
            (max.toFloat / bitmap.getWidth * bitmap.getHeight).toInt, true)
          smaller.compress(Bitmap.CompressFormat.JPEG, 85, new FileOutputStream(image_f))
        }
        get_caption(event_name) { caption =>
          try_upload(event_id, caption)
        }
      case _ => Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show()
    }
  }
  
  def try_upload(event_id: String, caption: String) {
    val loading = new ProgressDialog(Meetups.this)
    loading.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
    loading.setTitle("Posting Photo to Meetup")
    loading.show()
    val Some(cli) = Account.client(prefs)
    http on_error {
      case e => 
        Log.e("Meetups", "Error uploading photo", e)
        post {
          loading.dismiss()
          failed_dialog(event_id, caption)
        }
    } future (
      cli(PhotoUpload.event_id(event_id).caption(caption).photo(image_f)) >?> { total => 
        post { loading.setMax(total.toInt) }
        (bytes) => { post { loading.setProgress(bytes.toInt) } }
      } >> { stm =>
        image_f.delete()
        post { loading.dismiss() }
      }
    )
  }
  
  def get_caption[T](event_name: String)(block: String => Unit) {
    val input = new EditText(this)
    val dialog = new AlertDialog.Builder(this)
      .setTitle(event_name)
      .setMessage("Photo Caption: (optional)")
      .setPositiveButton("Post", new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, button: Int) {
          block(input.getText.toString)
        }
      }).setNegativeButton("Cancel", clean_image())
      .setOnCancelListener(clean_image())
      .create()
    dialog.setView(input, 15, 15, 15, 15)
    dialog.show()
  }
  
  def failed_dialog(event_id: String, caption: String) {
    new AlertDialog.Builder(this)
      .setTitle(R.string.upload_failed_title)
      .setMessage(R.string.upload_failed)
      .setPositiveButton("Retry", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, which: Int) { try_upload(event_id, caption) }
      })
      .setNeutralButton("Okay", clean_image())
      .setOnCancelListener(clean_image())
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