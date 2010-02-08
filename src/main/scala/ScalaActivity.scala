package snapup

import android.os.Handler
import android.app.Activity
import android.content.{DialogInterface,SharedPreferences}
import android.widget.TextView
import android.view.View

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
  def text_in(parent: { def findViewById(id: Int): View })(id: Int)(text: String) =
    parent.findViewById(id).asInstanceOf[TextView].setText(text)

  implicit def sp2editing(sp: SharedPreferences) = new EditingContext(sp)
  class EditingContext(sp: SharedPreferences) {
    def editor(block: SharedPreferences.Editor => Unit) = {
      val editor = sp.edit()
      block(editor)
      editor.commit()
    }
  }
}