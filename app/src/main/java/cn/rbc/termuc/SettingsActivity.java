package cn.rbc.termuc;
import android.content.*;
import android.os.*;
import android.preference.*;
import android.view.*;

public class SettingsActivity extends PreferenceActivity
implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener
{
	private final static String TAG = "SettingsActivity", FT = "f";

    private CheckBoxPreference mPureModePref, mWordWrapPref, mWhitespacePref, mUseSpacePref,
            mShowHidden, mSuggestionPref, mAutoCapsPref;
	private EditTextPreference mCFlagsPref, mHost, mPort;
	private ListPreference mThemePref, mFontPref, mSizePref, mTabSizePref, mEngine;

	private boolean mPure, mWrap, mSpace, mUseSpace, mSuggestion, mAutoCaps;
	private String mTheme, mComp, mFont, tpFont;
	private int mTabSize;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        Utils.setNightMode(this, Application.theme);
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		addPreferencesFromResource(R.xml.settings);

		mThemePref = (ListPreference)findPreference(Application.KEY_THEME);
        mPureModePref = (CheckBoxPreference)findPreference(Application.KEY_PUREMODE);
		mFontPref = (ListPreference)findPreference(Application.KEY_FONT);
		mFontPref.setOnPreferenceChangeListener(this);
		mSizePref = (ListPreference)findPreference(Application.KEY_TEXTSIZE);
		mWordWrapPref = (CheckBoxPreference)findPreference(Application.KEY_WORDWRAP);
		mWhitespacePref = (CheckBoxPreference)findPreference(Application.KEY_WHITESPACE);
		mUseSpacePref = (CheckBoxPreference)findPreference(Application.KEY_USESPACE);
		mTabSizePref = (ListPreference)findPreference(Application.KEY_TABSIZE);
        mSuggestionPref = (CheckBoxPreference)findPreference(Application.KEY_SUGGUESTION);
        mAutoCapsPref = (CheckBoxPreference)findPreference(Application.KEY_AUTOCAPS);
		mShowHidden = (CheckBoxPreference)findPreference(Application.KEY_SHOW_HIDDEN);
		mCFlagsPref = (EditTextPreference)findPreference(Application.KEY_CFLAGS);
		mEngine = (ListPreference)findPreference(Application.KEY_COMPLETION);
		mEngine.setOnPreferenceChangeListener(this);
		mHost = (EditTextPreference)findPreference(Application.KEY_LSP_HOST);
		mPort = (EditTextPreference)findPreference(Application.KEY_LSP_PORT);
		findPreference(Application.KEY_CHECKAPP).setOnPreferenceClickListener(this);
		findPreference(Application.KEY_INITAPP).setOnPreferenceClickListener(this);
		onPreferenceChange(mEngine, mEngine.getValue());

        mTheme = Application.theme;
        mPure = Application.pure_mode;
		mWrap = Application.wordwrap;
		mSpace = Application.whitespace;
		mUseSpace = Application.usespace;
		mTabSize = Application.tabsize;
        mSuggestion = Application.suggestion;
        mAutoCaps = Application.auto_caps;
		mComp = Application.completion;
		mFont = tpFont = Application.font;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				break;
		}
		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference p1) {
		if (Application.KEY_CHECKAPP.equals(p1.getKey()))
			Utils.testApp(this, true);
		else
			Utils.initBack(this, true);
		return true;
	}

	@Override
	public boolean onPreferenceChange(Preference p1, Object p2) {
		if (p1.compareTo(mEngine)==0) {
			boolean enable = "s".equals(p2);
			mHost.setEnabled(enable);
			mPort.setEnabled(enable);
		} else if (p1.compareTo(mFontPref)==0) {
			if ("c".equals(p2)) {
				Intent it = new Intent(this, FileActivity.class);
				it.putExtra(FileActivity.FN,
					getPreferenceManager().getSharedPreferences().getString(Application.KEY_MYFONT, null));
				startActivityForResult(it, 0);
				return false;
			}
			tpFont = (String)p2;
		}
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode==0 && resultCode==RESULT_OK) {
			SharedPreferences.Editor edt = getPreferenceManager().getSharedPreferences().edit();
			edt.putString(Application.KEY_MYFONT, tpFont = data.getStringExtra(FileActivity.FN));
			edt.commit();
			mFontPref.setValue("c");
		}
	}

	@Override
	public void onBackPressed() {
		setResult(mTheme != mThemePref.getValue()
				  ? RESULT_FIRST_USER : 
				  (mPure == mPureModePref.isChecked()
                  && mFont == tpFont
				  && mWrap == mWordWrapPref.isChecked()
				  && mSpace == mWhitespacePref.isChecked()
				  && mUseSpace == mUseSpacePref.isChecked()
				  && mTabSize == Integer.parseInt(mTabSizePref.getValue())
                  && mSuggestion == mSuggestionPref.isChecked()
                  && mAutoCaps == mAutoCapsPref.isChecked()
				  && mComp.equals(mEngine.getValue()))
				  ? RESULT_CANCELED : RESULT_OK);
		super.onBackPressed();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(FT, tpFont);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		tpFont = state.getString(FT);
	}

	@Override
	protected void onPause() {
		Application.theme = mThemePref.getValue();
        Application.pure_mode = mPureModePref.isChecked();
		Application.wordwrap = mWordWrapPref.isChecked();
		Application.whitespace = mWhitespacePref.isChecked();
		Application.usespace = mUseSpacePref.isChecked();
		Application.tabsize = Integer.parseInt(mTabSizePref.getValue());
		Application.font = tpFont;
		Application.textsize = Integer.parseInt(mSizePref.getValue());
        Application.suggestion = mSuggestionPref.isChecked();
        Application.auto_caps = mAutoCapsPref.isChecked();
		Application.show_hidden = mShowHidden.isChecked();
		Application.cflags = mCFlagsPref.getText();
		Application.completion = mEngine.getValue();
		Application.lsp_host = mHost.getText();
		Application.lsp_port = Integer.parseInt(mPort.getText());
		super.onPause();
	}
}
