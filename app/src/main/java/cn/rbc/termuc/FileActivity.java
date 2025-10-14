package cn.rbc.termuc;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class FileActivity extends Activity
implements ListView.OnItemClickListener, FileFilter
{
	private ListView lv;
	private FileAdapter adp;
	private File pwd;
	final static String FN = "n", PD = "p";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Utils.setNightMode(this, Application.theme);
		super.onCreate(savedInstanceState);
		ActionBar ab = getActionBar();
		ab.setHomeButtonEnabled(true);
		setTitle(R.string.select_font);
		String path = getIntent().getStringExtra(FN);
		if (path != null)
			pwd = new File(path).getParentFile();
		else pwd = Utils.ROOT;
		lv = new ListView(this);
		lv.setLayoutParams(new ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		));
		lv.setFastScrollEnabled(true);
		lv.setAdapter(adp = new FileAdapter(this, pwd, this));
		lv.setOnItemClickListener(this);
		setContentView(lv);
	}

	@Override
	public void onItemClick(AdapterView<?> av, View v, int i, long n) {
		String _it = adp.getItem(i).name;
		if ("..".equals(_it))
			pwd = pwd.getParentFile();
		else {
			File f = new File(pwd, _it);
			if (f.isDirectory())
				pwd = f;
			else {
				Intent it = getIntent();
				it.putExtra(FN, f.getAbsolutePath());
				setResult(RESULT_OK, it);
				finish();
				return;
			}
		}
		refresh();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(PD, pwd.getAbsolutePath());
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		pwd = new File(savedInstanceState.getString(PD));
	}

	public boolean accept(File p1) {
		String p2;
		return p1.isDirectory() || ((p2=p1.getName()).endsWith(".ttf") || p2.endsWith(".otf"));
	}

	private void refresh() {
		getActionBar().setSubtitle(pwd.getAbsolutePath());
		adp.setPath(pwd);
		adp.notifyDataSetChanged();
	}

	@Override
	public void onBackPressed() {
		if (Utils.ROOT.compareTo(pwd)==0)
			super.onBackPressed();
		else {
			pwd = pwd.getParentFile();
			refresh();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}
}
