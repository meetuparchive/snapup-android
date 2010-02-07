package snapup

import com.meetup.snapup.R

import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{ArrayAdapter, ListView, Toast, EditText, TextView, ImageView, Button}
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
import dispatch.mime.Mime._
import java.io.{File,FileOutputStream}

import net.liftweb.json._
import net.liftweb.json.JsonAST._

class Members extends ListActivity with ScalaActivity {
  lazy val prefs = new Prefs(this)
  val http = AndroidHttp

  def extras(str: String) = getIntent.getExtras.getString(str)
  lazy val event_name = extras("event_name")
  lazy val event_id = extras("event_id")
  lazy val group_name = extras("group_name")
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.members)
    val Some(cli) = Account.client(prefs)
    text_in(this)(R.id.event_name)(event_name)
    text_in(this)(R.id.group_name)(group_name)
    text_in(this)(R.id.event_time)(extras("event_time"))
    val snap_photo = findViewById(R.id.snap_photo).asInstanceOf[Button];
    snap_photo.setOnClickListener {
      startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image_f)), 0)
    }
    http.future(cli(Rsvps.event_id(event_id)) ># { json =>
      val rsvps = Response.results(json).filter { rsvp =>
        Rsvp.response(rsvp) exists { r => r == "yes" || r == "maybe" }
      }.toArray
      post { 
        setListAdapter(new ArrayAdapter(this, R.layout.row, rsvps) {
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
          override def isEnabled(pos: Int) = false
        })
      }
    })
  }

  val image_f = new File(Environment.getExternalStorageDirectory, "snapup-temp.jpg")
  def clean_image() { image_f.delete() }

  override def onActivityResult(index: Int, result: Int, intent: Intent) {
    result match {
      case Activity.RESULT_OK =>
        // insert as bitmap so gallery will own the file
        val bitmap = BitmapFactory.decodeFile(image_f.getPath)
        MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, event_name, group_name)
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
    val loading = new ProgressDialog(Members.this)
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
        post { 
          loading.dismiss() 
          Toast.makeText(this, R.string.photo_uploaded, Toast.LENGTH_LONG).show()
        }
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
    dialog.setOnDismissListener(input.setVisibility(View.GONE))
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
}