package snapup

import android.os.Handler
import android.app.Activity
import android.content.DialogInterface
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
  def text_in(parent: { def findViewById(id: Int): View })(id: Int)(text: String) =
    parent.findViewById(id).asInstanceOf[TextView].setText(text)
}