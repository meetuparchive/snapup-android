package snapup

import android.os.Handler
import android.app.Activity
import android.content.{DialogInterface,SharedPreferences, Context}
import android.widget.{TextView, ImageView, ArrayAdapter}
import android.view.{View, ViewGroup}

trait ScalaActivity extends Activity {
  val handler = new Handler
  def post(block: => Unit) { 
    handler.post(new Runnable{
      def run { block }
    })
  }
  implicit def f2cancel(block: => Unit) = new DialogInterface.OnCancelListener {
    def onCancel(dialog: DialogInterface) { block }
  }
  implicit def f2click(block: => Unit) = new DialogInterface.OnClickListener {
    def onClick(dialog: DialogInterface, which: Int) { block }
  }
  implicit def f2dismissal(block: => Unit) = new DialogInterface.OnDismissListener {
    def onDismiss(dialog: DialogInterface) { block }
  }
  implicit def f2viewclick(block: => Unit) = new View.OnClickListener {
    def onClick(view: View) { block }
  }
  def text_!(id: Int) = findViewById(id).asInstanceOf[TextView]

  implicit def sp2editing(sp: SharedPreferences) = new EditingContext(sp)
  class EditingContext(sp: SharedPreferences) {
    def editor(block: SharedPreferences.Editor => Unit) = {
      val editor = sp.edit()
      block(editor)
      editor.commit()
    }
  }
  implicit def view2casting(view: View) = new ViewCaster(view)
  class ViewCaster(view: View){
    def text_!(id: Int) = view.findViewById(id).asInstanceOf[TextView]
    def image_!(id: Int) = view.findViewById(id).asInstanceOf[ImageView]
  }
}

abstract class ArrayReusingAdapter[T <: AnyRef, R](content: Context, id: Int, seq: Array[T]) extends ArrayAdapter(content, id, seq) {
  override def getView(position: Int, convertView: View, parent: ViewGroup) = {
    def getTag(view: View) = view.getTag.asInstanceOf[Tuple2[Int, R]]
    val (view, row) = convertView match {
      case null =>  val nv = inflateView; (nv, setupRow(nv))
      case convertView => (convertView, getTag(convertView)._2)
    }
    view.setTag((position, row))
    draw(position, row, position == getTag(view)._1)
    view
  }
  def inflateView: View
  def setupRow(view: View): R
  def draw(position: Int, row: R, current: => Boolean)
}
