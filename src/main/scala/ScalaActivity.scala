package snapup

import android.os.Handler
import android.app.Activity
import android.content.{DialogInterface,SharedPreferences, Context}
import android.widget.{TextView, ImageView, ArrayAdapter}
import android.view.{View, ViewGroup}

trait ScalaActivity extends Activity with PrefEditing with com.meetup.snapup.TypedActivity {
  val handler = new Handler
  def post(block: => Unit) { 
    handler.post(new Runnable{
      def run { block }
    })
  }
  implicit def f2cancel(block: () => Unit) = new DialogInterface.OnCancelListener {
    def onCancel(dialog: DialogInterface) { block() }
  }
  implicit def f2click(block: () => Unit) = new DialogInterface.OnClickListener {
    def onClick(dialog: DialogInterface, which: Int) { block() }
  }
  implicit def f2dismissal(block: () => Unit) = new DialogInterface.OnDismissListener {
    def onDismiss(dialog: DialogInterface) { block() }
  }
  implicit def f2viewclick(block: () => Unit) = new View.OnClickListener {
    def onClick(view: View) { block() }
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
