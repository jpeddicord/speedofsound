package net.codechunk.speedofsound;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class AboutActivity extends Activity
{
	public static final String TAG = "AboutActivity";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.about);

		PackageInfo pi;
		try
		{
			pi = this.getPackageManager().getPackageInfo(
					this.getPackageName(), 0);
		}
		catch (NameNotFoundException e)
		{
			Log.e(TAG, "Couldn't get package information?!");
			return;
		}

		List<Spanned> items = new ArrayList<Spanned>();
		items.add(Html.fromHtml(String.format("%s <strong>%s</strong>",
				this.getString(R.string.version), pi.versionName)));
		items.add(Html.fromHtml(this.getString(R.string.contact)));
		items.add(Html.fromHtml(this.getString(R.string.help_translate)));
		items.add(Html.fromHtml(this.getString(R.string.github)));

		ListView list = (ListView) this.findViewById(R.id.about_listview);
		list.setAdapter(new ArrayAdapter<Spanned>(this,
				android.R.layout.simple_list_item_1,
				android.R.id.text1,
				items));

		list.setOnItemClickListener(this.listener);
	}

	private OnItemClickListener listener = new AdapterView.OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			Uri uri = null;

			// XXX: probably a better way to do this
			switch (position)
			{
				case 0:
					try
					{
						uri = Uri.parse("market://details?id=net.codechunk.speedofsound");
						AboutActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, uri));
					}
					catch (ActivityNotFoundException e)
					{
						uri = Uri.parse("https://play.google.com/store/apps/details?id=net.codechunk.speedofsound");
						AboutActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, uri));
					}
					return;
				case 1:
					Intent email = new Intent(Intent.ACTION_SEND);
					email.setType("plain/text");
					email.putExtra(Intent.EXTRA_EMAIL, new String[] { "mobile@codechunk.net" });
					email.putExtra(Intent.EXTRA_SUBJECT, "Speed of Sound");
					AboutActivity.this.startActivity(email);
					return;
				case 2:
					uri = Uri.parse("https://www.transifex.com/projects/p/speedofsound/");
					break;
				case 3:
					uri = Uri.parse("https://github.com/jpeddicord/speedofsound");
					break;
				default:
					return;
			}

			AboutActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, uri));
		}
	};

}
