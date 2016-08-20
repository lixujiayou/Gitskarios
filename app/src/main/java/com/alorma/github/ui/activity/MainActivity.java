package com.alorma.github.ui.activity;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.alorma.github.AccountsHelper;
import com.alorma.github.BuildConfig;
import com.alorma.github.GitskariosSettings;
import com.alorma.github.R;
import com.alorma.github.StoreCredentials;
import com.alorma.github.presenter.NavigationFragment;
import com.alorma.github.sdk.bean.dto.response.Notification;
import com.alorma.github.sdk.bean.dto.response.User;
import com.alorma.github.sdk.services.notifications.GetNotificationsClient;
import com.alorma.github.ui.ErrorHandler;
import com.alorma.github.ui.activity.base.BaseActivity;
import com.alorma.github.ui.fragment.GeneralPeopleFragment;
import com.alorma.github.ui.fragment.events.EventsListFragment;
import com.alorma.github.ui.fragment.gists.AuthUserGistsFragment;
import com.alorma.github.ui.fragment.gists.AuthUserStarredGistsFragment;
import com.alorma.github.ui.fragment.issues.GeneralIssuesListFragment;
import com.alorma.github.ui.fragment.orgs.OrgsReposFragment;
import com.alorma.github.ui.fragment.repos.GeneralReposFragment;
import com.alorma.github.ui.utils.DrawerImage;
import com.alorma.github.utils.AccountUtils;
import com.mikepenz.actionitembadge.library.ActionItemBadge;
import com.mikepenz.actionitembadge.library.utils.BadgeStyle;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.ExpandableDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.util.DrawerImageLoader;
import com.mikepenz.octicons_typeface_library.Octicons;
import java.util.ArrayList;
import java.util.List;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends BaseActivity implements NavigationFragment.NavigationCallback {

  private Account selectedAccount;
  private Fragment lastUsedFragment;
  private Drawer resultDrawer;
  private int notificationsSizeCount = 0;
  private NavigationFragment navigationFragment;
  private AccountHeader accountHeader;

  public static void startActivity(Activity context) {
    Intent intent = new Intent(context, MainActivity.class);
    context.startActivity(intent);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    AccountsManager accountsFragment = new AccountsManager();
    List<Account> accounts = accountsFragment.getAccounts(this);

    if (accounts.isEmpty()) {
      Intent intent = new Intent(this, WelcomeActivity.class);
      startActivity(intent);
      finish();
    }

    setContentView(R.layout.generic_toolbar_responsive);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    boolean changeLog = checkChangeLog();
    if (changeLog) {
      View view = findViewById(R.id.content);
      Snackbar.make(view, R.string.app_updated, Snackbar.LENGTH_LONG).setAction("Changelog", v -> {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://gitskarios.github.io")));
      }).show();
    }
  }

  private boolean checkChangeLog() {
    if (getSupportFragmentManager() != null) {
      int currentVersion = BuildConfig.VERSION_CODE;
      GitskariosSettings settings = new GitskariosSettings(this);
      int version = settings.getVersion(0);

      if (currentVersion > version) {
        settings.saveVersion(currentVersion);
        return true;
      }
    }

    return false;
  }

  @Override
  public void onStart() {
    super.onStart();

    if (resultDrawer == null) {
      List<Account> accounts = getAccounts();
      if (!accounts.isEmpty()) {
        selectedAccount = accounts.get(0);
        createDrawer();
        selectAccount(selectedAccount);
        onUserEventsSelected();
      }
    }

    navigationFragment.setNavigationCallback(this);
  }

  @Override
  protected void onStop() {
    navigationFragment.setNavigationCallback(null);
    super.onStop();
  }

  private String getUserExtraName(Account account) {
    String accountName = getNameFromAccount(account);
    String userMail = AccountsHelper.getUserMail(this, account);
    String userName = AccountsHelper.getUserName(this, account);
    if (!TextUtils.isEmpty(userMail)) {
      return userMail;
    } else if (!TextUtils.isEmpty(userName)) {
      return userName;
    }
    return accountName;
  }

  private String getNameFromAccount(Account account) {
    return new AccountUtils().getNameFromAccount(account.name);
  }

  private void createDrawer() {
    accountHeader = buildHeader();
    DrawerBuilder drawer = new DrawerBuilder();
    drawer.withActivity(this);
    drawer.withToolbar(getToolbar());
    drawer.withAccountHeader(accountHeader, true);

    List<IDrawerItem> userItems = getUserDrawerItems();

    for (IDrawerItem userItem : userItems) {
      drawer.addDrawerItems(userItem);
    }

    List<IDrawerItem> allProfilesItems = getStickyDrawerItems();
    for (IDrawerItem allProfilesItem : allProfilesItems) {
      drawer.addStickyDrawerItems(allProfilesItem);
    }

    drawer.withOnDrawerItemClickListener((view, position, drawerItem) -> {
      if (drawerItem != null) {
        long identifier = drawerItem.getIdentifier();
        switch ((int) identifier) {
          case R.id.nav_drawer_notifications:
            openNotifications();
            break;
          case R.id.nav_drawer_settings:
            onSettingsSelected();
            break;
          case R.id.nav_drawer_sign_out:
            signOut();
            return true;
        }
      }
      return false;
    });
    resultDrawer = drawer.build();
    resultDrawer.setSelection(R.id.nav_drawer_events);
  }

  private List<IDrawerItem> getUserDrawerItems() {
    int iconColor = ContextCompat.getColor(this, R.color.icons);

    List<IDrawerItem> items = new ArrayList<>();

    items.add(new PrimaryDrawerItem().withName(R.string.menu_events)
        .withIcon(Octicons.Icon.oct_calendar)
        .withIconColor(iconColor)
        .withIdentifier(R.id.nav_drawer_events)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onUserEventsSelected();
          return false;
        }));
    items.add(new PrimaryDrawerItem().withName(R.string.navigation_general_repositories)
        .withIcon(Octicons.Icon.oct_repo)
        .withIconColor(iconColor)
        .withIdentifier(R.id.nav_drawer_repositories)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onReposSelected();
          return false;
        }));
    items.add(new PrimaryDrawerItem().withName(R.string.navigation_people)
        .withIcon(Octicons.Icon.oct_organization)
        .withIconColor(iconColor)
        .withIdentifier(R.id.nav_drawer_people)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onPeopleSelected();
          return false;
        }));
    items.add(new PrimaryDrawerItem().withName(R.string.navigation_issues)
        .withIcon(Octicons.Icon.oct_issue_opened)
        .withIconColor(iconColor)
        .withIdentifier(R.id.nav_drawer_issues)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onIssuesSelected();
          return false;
        }));

    PrimaryDrawerItem myGistsDrawerItem = new PrimaryDrawerItem().withName(R.string.navigation_my_gists)
        .withIdentifier(R.id.nav_drawer_gists)
        .withLevel(2)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onGistsSelected();
          return false;
        });
    PrimaryDrawerItem starredGistsDrawerItem = new PrimaryDrawerItem().withName(R.string.navigation_gists_starred)
        .withIdentifier(R.id.nav_drawer_gists_starred)
        .withLevel(2)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onStarredGistsSelected();
          return false;
        });
    items.add(new ExpandableDrawerItem().withName(R.string.navigation_gists)
        .withSubItems(myGistsDrawerItem, starredGistsDrawerItem)
        .withIcon(Octicons.Icon.oct_gist)
        .withIconColor(iconColor)
        .withSelectable(false));

    return items;
  }

  private List<IDrawerItem> getStickyDrawerItems() {
    int iconColor = ContextCompat.getColor(this, R.color.icons);

    List<IDrawerItem> items = new ArrayList<>();
    items.add(new SecondaryDrawerItem().withName(R.string.menu_enable_notifications)
        .withIdentifier(R.id.nav_drawer_notifications)
        .withSelectable(false)
        .withIcon(Octicons.Icon.oct_bell)
        .withIconColor(iconColor));

    items.add(new SecondaryDrawerItem().withName(R.string.navigation_settings)
        .withIcon(Octicons.Icon.oct_gear)
        .withIconColor(iconColor)
        .withIdentifier(R.id.nav_drawer_settings)
        .withSelectable(false));

    items.add(new DividerDrawerItem());

    items.add(new SecondaryDrawerItem().withName(R.string.navigation_sign_out)
        .withIcon(Octicons.Icon.oct_sign_out)
        .withIconColor(iconColor)
        .withIdentifier(R.id.nav_drawer_sign_out)
        .withSelectable(false));

    return items;
  }

  private AccountHeader buildHeader() {
    DrawerImageLoader.init(new DrawerImage());

    AccountHeaderBuilder headerBuilder = new AccountHeaderBuilder().withActivity(this).withHeaderBackground(R.color.md_grey_600);

    headerBuilder.withOnAccountHeaderListener((view, profile, current) -> {
      if (current) {
        User user = new User();
        user.login = profile.getName().getText();
        Intent launcherIntent = ProfileActivity.createLauncherIntent(MainActivity.this, selectedAccount);
        startActivity(launcherIntent);
        return true;
      } else {
        if (profile instanceof ProfileDrawerItem) {
          List<IDrawerItem> subItems = ((ProfileDrawerItem) profile).getSubItems();
          if (subItems != null) {
            resultDrawer.removeAllItems();
            for (IDrawerItem subItem : subItems) {
              resultDrawer.addItems(subItem);
            }
          }
        }
        return true;
      }
    });

    headerBuilder.addProfiles(getUserDrawerItem());

    return headerBuilder.build();
  }

  @NonNull
  private ProfileDrawerItem getOrganizationProfileDrawerItem(com.alorma.github.sdk.core.User user) {
    int iconColor = ContextCompat.getColor(this, R.color.icons);

    ProfileDrawerItem orgProfile = new ProfileDrawerItem().withName(user.getLogin()).withIcon(user.getAvatar());

    orgProfile.withSubItems(new PrimaryDrawerItem().withName("Events")
        .withIcon(Octicons.Icon.oct_calendar)
        .withIconColor(iconColor)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onOrgEventsSelected(user.getLogin());
          return false;
        }));
    orgProfile.withSubItems(new PrimaryDrawerItem().withName("Repositories")
        .withIcon(Octicons.Icon.oct_repo)
        .withIconColor(iconColor)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onOrgReposSelected(user.getLogin());
          return false;
        }));
    orgProfile.withSubItems(new PrimaryDrawerItem().withName("People")
        .withIcon(Octicons.Icon.oct_organization)
        .withIconColor(iconColor)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onOrgPeopleSelected(user.getLogin());
          return false;
        }));
    orgProfile.withSubItems(new PrimaryDrawerItem().withName("Teams")
        .withIcon(Octicons.Icon.oct_jersey)
        .withIconColor(iconColor)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onOrgTeamsSelected(user.getLogin());
          return false;
        }));
    orgProfile.withSubItems(new PrimaryDrawerItem().withName("Issues")
        .withIcon(Octicons.Icon.oct_issue_opened)
        .withIconColor(iconColor)
        .withOnDrawerItemClickListener((view, position, drawerItem) -> {
          onOrgIssuesSelected(user.getLogin());
          return false;
        }));
    return orgProfile;
  }

  private ProfileDrawerItem getUserDrawerItem() {
    String userAvatar = AccountsHelper.getUserAvatar(this, selectedAccount);
    ProfileDrawerItem userDrawerItem = new ProfileDrawerItem().withName(getUserExtraName(selectedAccount))
        .withEmail(getNameFromAccount(selectedAccount))
        .withNameShown(false)
        .withSubItems(getUserDrawerItems())
        .withIdentifier(selectedAccount.hashCode());
    if (!TextUtils.isEmpty(userAvatar)) {
      userDrawerItem.withIcon(userAvatar);
    }
    return userDrawerItem;
  }

  private void selectAccount(final Account account) {
    boolean changingUser = selectedAccount != null && !getNameFromAccount(selectedAccount).equals(getNameFromAccount(account));
    this.selectedAccount = account;

    accountNameProvider.setName(getNameFromAccount(account));

    loadUserOrgs();

    StoreCredentials credentials = new StoreCredentials(MainActivity.this);
    credentials.clear();
    String authToken = AccountsHelper.getUserToken(this, account);

    credentials.storeToken(authToken);
    credentials.storeUsername(getNameFromAccount(account));

    credentials.storeUrl(AccountsHelper.getUrl(this, account));

    String url = AccountsHelper.getUrl(this, account);

    credentials.storeUrl(url);

    if (changingUser) {
      lastUsedFragment = null;
    }
  }

  private void loadUserOrgs() {
    navigationFragment = new NavigationFragment();

    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    ft.add(new NavigationFragment(), "navigation");
    ft.commit();

    navigationFragment.setNavigationCallback(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    getMenuInflater().inflate(R.menu.main_menu, menu);
    menu.findItem(R.id.action_search)
        .setIcon(new IconicsDrawable(this, GoogleMaterial.Icon.gmd_search).color(Color.WHITE).sizeDp(24).respectFontBounds(true));

    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    if (notificationsSizeCount > 0) {
      BadgeStyle badgeStyle =
          new BadgeStyle(BadgeStyle.Style.DEFAULT, R.layout.menu_action_item_badge, getResources().getColor(R.color.accent),
              getResources().getColor(R.color.accent_dark), Color.WHITE, getResources().getDimensionPixelOffset(R.dimen.gapMicro));
      ActionItemBadge.update(this, menu.findItem(R.id.action_notifications), Octicons.Icon.oct_bell, badgeStyle, notificationsSizeCount);
    } else {
      ActionItemBadge.hide(menu.findItem(R.id.action_notifications));
    }

    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkNotifications();
  }

  private void checkNotifications() {
    GetNotificationsClient client = new GetNotificationsClient();
    client.observable()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<List<Notification>>() {
          @Override
          public void onCompleted() {

          }

          @Override
          public void onError(Throwable e) {

          }

          @Override
          public void onNext(List<Notification> notifications) {
            notificationsSizeCount = notifications.size();
            invalidateOptionsMenu();
          }
        });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    if (item.getItemId() == R.id.action_search) {
      Intent intent = SearchActivity.launchIntent(this);
      startActivity(intent);
    } else if (item.getItemId() == R.id.action_notifications) {
      openNotifications();
    }

    return false;
  }

  private void openNotifications() {
    Intent intent = NotificationsActivity.launchIntent(this);
    startActivity(intent);
  }

  private void setFragment(Fragment fragment) {
    setFragment(fragment, true);
  }

  private void setFragment(Fragment fragment, boolean addToBackStack) {
    try {
      if (fragment != null && getSupportFragmentManager() != null) {
        this.lastUsedFragment = fragment;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (ft != null) {
          ft.replace(R.id.content, fragment);
          if (addToBackStack) {
            ft.addToBackStack(null);
          }
          ft.commit();
        }
      }
    } catch (Exception e) {
      ErrorHandler.onError(this, "MainActivity.setFragment()", e);
    }
  }

  public boolean onReposSelected() {
    setFragment(GeneralReposFragment.newInstance(), false);
    return true;
  }

  public boolean onPeopleSelected() {
    setFragment(GeneralPeopleFragment.newInstance(), false);
    return false;
  }

  public boolean onIssuesSelected() {
    setFragment(GeneralIssuesListFragment.newInstance(), false);
    return false;
  }

  public boolean onGistsSelected() {
    AuthUserGistsFragment gistsFragment = AuthUserGistsFragment.newInstance();
    setFragment(gistsFragment);
    return false;
  }

  public boolean onStarredGistsSelected() {
    AuthUserStarredGistsFragment gistsFragment = AuthUserStarredGistsFragment.newInstance();
    setFragment(gistsFragment);
    return false;
  }

  public boolean onUserEventsSelected() {
    String user = new StoreCredentials(this).getUserName();
    if (user != null) {
      setFragment(EventsListFragment.newInstance(user), false);
    }
    return true;
  }

  public void onOrgEventsSelected(String orgName) {

  }

  public void onOrgReposSelected(String orgName) {
    OrgsReposFragment orgsReposFragment = OrgsReposFragment.newInstance(orgName);
    setFragment(orgsReposFragment, true);
  }

  public void onOrgPeopleSelected(String orgName) {

  }

  public void onOrgTeamsSelected(String orgName) {

  }

  public void onOrgIssuesSelected(String orgName) {

  }

  public boolean onSettingsSelected() {
    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
    return false;
  }

  public void signOut() {
    if (selectedAccount != null) {
      removeAccount(selectedAccount, () -> {
        StoreCredentials storeCredentials = new StoreCredentials(MainActivity.this);
        storeCredentials.clear();

        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
      });
    }
  }

  @Override
  public void onBackPressed() {
    if (resultDrawer != null && resultDrawer.isDrawerOpen()) {
      resultDrawer.closeDrawer();
    } else {
      if (lastUsedFragment instanceof EventsListFragment) {
        finish();
      } else if (resultDrawer != null) {
        resultDrawer.setSelection(R.id.nav_drawer_events);
      }
    }
  }

  @Override
  public void onOrganizationsLoaded(List<com.alorma.github.sdk.core.User> organizations) {
    if (accountHeader != null) {
      for (com.alorma.github.sdk.core.User organization : organizations) {
        ProfileDrawerItem drawerItem = getOrganizationProfileDrawerItem(organization);
        accountHeader.addProfiles(drawerItem);
      }
    }
  }
}
