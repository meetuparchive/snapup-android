package snapup

import com.meetup.snapup.R

import android.app.{Activity, ListActivity, AlertDialog, ProgressDialog}
import android.os.{Bundle, Environment, Looper, Handler, Message}
import android.widget.{ListView, Toast, EditText, TextView, ImageView, Button}
import android.view.View
import android.net.Uri
import android.provider.MediaStore
import android.content.{Intent, DialogInterface}
import android.graphics.{BitmapFactory,Bitmap}
import android.graphics.drawable.ColorDrawable
import android.util.Log

import dispatch.meetup._
import dispatch.Http
import dispatch.Http._
import dispatch.liftjson.Js._
import dispatch.mime.Mime._
import java.io.{File,FileOutputStream}

import net.liftweb.json._
import net.liftweb.json.JsonAST._


object RsvpCache extends HttpCache[Array[JValue]] {
  def apply(prefs: Prefs) = load { event_id =>
    val Some(cli) = Account.client(prefs)
    cli(Rsvps.event_id(event_id)) ># { json =>
      Response.results(json).filter { rsvp =>
        Rsvp.response(rsvp) exists { r => r == "yes" || r == "maybe" }
      }.toArray
    }
  } _
}

class Members extends ListActivity with ScalaActivity {
  lazy val prefs = new Prefs(this)
  val http = AndroidHttp
  val blank = new ColorDrawable

  def extras(str: String) = getIntent.getExtras.getString(str)
  lazy val event_name = extras("event_name")
  lazy val event_id = extras("event_id")
  lazy val group_name = extras("group_name")
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val Some(cli) = Account.client(prefs)
    setContentView(R.layout.members)
    text_!(R.id.event_name).setText(event_name)
    text_!(R.id.group_name).setText(group_name)
    text_!(R.id.event_time).setText(extras("event_time"))
    val snap_photo = findViewById(R.id.snap_photo).asInstanceOf[Button];
    snap_photo.setOnClickListener { () =>
      startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image_f)), 0)
    }
    RsvpCache(prefs)(event_id) { rsvps =>
      post { 
        case class Row(name: TextView, response: TextView, image: ImageView)
        setListAdapter(new ArrayReusingAdapter[JValue, Row](this, R.layout.row, rsvps) {
          def inflateView = View.inflate(Members.this, R.layout.row, null)
          def setupRow(view: View) = Row (view.text_!(R.id.event_name), view.text_!(R.id.group_name), view.image_!(R.id.icon))
          def draw(position: Int, row: Row, current: => Boolean) {
            val rsvp = rsvps(position)
            Rsvp.name(rsvp).foreach(row.name.setText)
            Rsvp.response(rsvp).map { case "maybe" => "Maybe"; case _ => "" }.foreach(row.response.setText)
            row.image.setImageDrawable(blank)
            Rsvp.photo_url(rsvp).foreach { url =>
              if (url.length > 0) 
                ImageCache.thumb(url) { bmp => post { if (current) row.image.setImageBitmap(bmp) } }
            }
          }
          override def isEnabled(pos: Int) = false
        })
      }
    }
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
      .setPositiveButton("Post", () => block(input.getText.toString))
      .setNegativeButton("Cancel", () => clean_image())
      .setOnCancelListener(() => clean_image())
      .create()
    dialog.setOnDismissListener(() => input.setVisibility(View.GONE))
    dialog.setView(input, 15, 15, 15, 15)
    dialog.show()
  }
  
  def failed_dialog(event_id: String, caption: String) {
    new AlertDialog.Builder(this)
      .setTitle(R.string.upload_failed_title)
      .setMessage(R.string.upload_failed)
      .setPositiveButton("Retry", () => try_upload(event_id, caption))
      .setNeutralButton("Okay", () => clean_image())
      .setOnCancelListener(() => clean_image())
      .create()
      .show()
  }
}