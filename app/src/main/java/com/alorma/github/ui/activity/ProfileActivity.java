package com.alorma.github.ui.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.alorma.github.R;
import com.alorma.github.sdk.bean.dto.response.Organization;
import com.alorma.github.sdk.bean.dto.response.User;
import com.alorma.github.sdk.login.AccountsHelper;
import com.alorma.github.sdk.services.client.GithubClient;
import com.alorma.github.sdk.services.orgs.GetOrgsClient;
import com.alorma.github.sdk.services.user.GetAuthUserClient;
import com.alorma.github.sdk.services.user.RequestUserClient;
import com.alorma.github.sdk.services.user.follow.CheckFollowingUser;
import com.alorma.github.sdk.services.user.follow.FollowUserClient;
import com.alorma.github.sdk.services.user.follow.UnfollowUserClient;
import com.alorma.github.ui.activity.base.BackActivity;
import com.alorma.github.ui.fragment.events.CreatedEventsListFragment;
import com.alorma.github.ui.fragment.repos.CurrentAccountReposFragment;
import com.alorma.github.ui.fragment.repos.UsernameReposFragment;
import com.alorma.github.ui.fragment.users.UserResumeFragment;
import com.alorma.github.ui.utils.PaletteUtils;
import com.alorma.gitskarios.core.client.StoreCredentials;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import jp.wasabeef.glide.transformations.CropCircleTransformation;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * Created by Bernat on 15/07/2014.
 */
public class ProfileActivity extends BackActivity implements UserResumeFragment.UserResumeCallback {

    public static final String EXTRA_COLOR = "EXTRA_COLOR";
    public static final String URL_PROFILE = "URL_PROFILE";
    private static final String USER = "USER";
    private static final String ACCOUNT = "ACCOUNT";
    private static final String AUTHENTICATED_USER = "AUTHENTICATED_USER";

    private User user;

    private boolean followingUser = false;
    private boolean updateProfile = false;
    private Account selectedAccount;
    private boolean colorApplied;
    private int avatarColor;

    @Bind(R.id.coordinator)
    CoordinatorLayout coordinatorLayout;

    @Bind(R.id.appbarLayout)
    AppBarLayout appBarLayout;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.ctlLayout)
    CollapsingToolbarLayout collapsingToolbarLayout;

    @Bind(R.id.userAvatar)
    ImageView userAvatar;

    @Bind(R.id.userLogin)
    TextView userLogin;

    @Bind(R.id.userName)
    TextView userName;

    @Bind(R.id.tabLayout)
    TabLayout tabLayout;

    @Bind(R.id.viewpager)
    ViewPager viewPager;

    private UserResumeFragment userResumeFragment;

    public static Intent createLauncherIntent(Context context, Account selectedAccount) {
        Intent intent = new Intent(context, ProfileActivity.class);
        Bundle extras = new Bundle();
        extras.putBoolean(AUTHENTICATED_USER, true);
        extras.putParcelable(ACCOUNT, selectedAccount);
        intent.putExtras(extras);
        return intent;
    }

    public static Intent createLauncherIntent(Context context, User user) {
        Bundle extras = new Bundle();
        if (user != null) {
            extras.putParcelable(USER, user);

            StoreCredentials settings = new StoreCredentials(context);
            extras.putBoolean(AUTHENTICATED_USER, user.login.equalsIgnoreCase(settings.getUserName()));
        }
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtras(extras);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_activity);

        ButterKnife.bind(this);

        setTitle("");
        collapsingToolbarLayout.setTitle("");
        collapsingToolbarLayout.setTitleEnabled(false);

        if (getIntent().getExtras().containsKey(EXTRA_COLOR)) {
            avatarColor = getIntent().getIntExtra(EXTRA_COLOR, ContextCompat.getColor(this, R.color.primary));
        }

        if (getSupportFragmentManager() != null && getSupportFragmentManager().getFragments() != null) {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof UserResumeFragment) {
                    userResumeFragment = (UserResumeFragment) fragment;
                }
            }
        }

        if (userResumeFragment == null) {
            userResumeFragment = new UserResumeFragment();
        }

        userResumeFragment.setUserResumeCallback(this);

    }

    @Override
    protected void getContent() {
        if (user == null) {
            GithubClient<User> requestClient;
            String avatar = null;
            String login = null;
            String name = null;
            if (getIntent().getExtras() != null) {
                if (getIntent().getExtras().containsKey(ACCOUNT)) {
                    selectedAccount = getIntent().getParcelableExtra(ACCOUNT);
                    avatar = AccountsHelper.getUserAvatar(this, selectedAccount);
                    login = selectedAccount.name;
                    name = AccountsHelper.getUserName(this, selectedAccount);
                }
                if (getIntent().getExtras().containsKey(USER)) {
                    user = getIntent().getParcelableExtra(USER);
                    avatar = user.avatar_url;
                    login = user.login;
                    name = user.name;
                }
            }

            if (login != null) {
                userLogin.setText(login);

                List<Fragment> fragments = new ArrayList<>();

                fragments.add(userResumeFragment);
                fragments.add(UsernameReposFragment.newInstance(login));
                fragments.add(CreatedEventsListFragment.newInstance(login));

                PagerAdapter adapter = new ProfilePagerAdapter(this, getSupportFragmentManager(), fragments);
                viewPager.setAdapter(adapter);
                viewPager.setOffscreenPageLimit(fragments.size());
                tabLayout.setupWithViewPager(viewPager);
            }

            if (name != null) {
                userName.setText(name);
            }

            if (avatar != null) {
                loadAvatar(avatar);
            }

            StoreCredentials settings = new StoreCredentials(this);

            if (user != null) {
                if (user.login.equalsIgnoreCase(settings.getUserName())) {
                    requestClient = new GetAuthUserClient(this);
                    updateProfile = true;
                } else {
                    requestClient = new RequestUserClient(this, user.login);
                }
            } else {
                requestClient = new GetAuthUserClient(this);
                updateProfile = true;
            }

            invalidateOptionsMenu();

            Observable<Integer> organizations = new GetOrgsClient(this, login).observable()
                    .map(new Func1<Pair<List<Organization>, Integer>, Integer>() {
                        @Override
                        public Integer call(Pair<List<Organization>, Integer> listIntegerPair) {
                            return listIntegerPair.first.size();
                        }
                    });

            Observable.combineLatest(requestClient.observable(), organizations, new Func2<User, Integer, User>() {
                @Override
                public User call(User user, Integer organizations) {
                    user.organizations = organizations;
                    return user;
                }
            })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<User>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onNext(User user) {
                            onUserLoaded(user);
                        }
                    });

        }
    }

    private void loadAvatar(String avatar) {
        Glide.with(this)
                .load(avatar)
                .bitmapTransform(new CropCircleTransformation(this))
                .into(userAvatar);

        Glide.with(this)
                .load(avatar)
                .asBitmap()
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                        Palette.from(resource).generate(new Palette.PaletteAsyncListener() {
                            @Override
                            public void onGenerated(Palette palette) {
                                Palette.Swatch profileSwatch = PaletteUtils.getProfileLightSwatch(palette);
                                if (profileSwatch == null) {
                                    profileSwatch = PaletteUtils.getProfileLightSwatch(palette);
                                }
                                if (profileSwatch == null) {
                                    profileSwatch = PaletteUtils.getProfileSwatch(palette);
                                }

                                if (profileSwatch != null) {
                                    applySwatchBackground(profileSwatch);
                                    applySwatchTexts(profileSwatch);
                                }
                            }
                        });
                    }
                });
    }

    private void applySwatchTexts(Palette.Swatch swatch) {
        int rgb = swatch.getTitleTextColor();
        generateAvatarBackground(rgb);
        userLogin.setTextColor(rgb);
        userName.setTextColor(rgb);

        tabLayout.setTabTextColors(swatch.getBodyTextColor(), rgb);
        tabLayout.setSelectedTabIndicatorColor(rgb);

        userResumeFragment.setColor(rgb);
    }

    private void generateAvatarBackground(int color) {
        ShapeDrawable circle = new ShapeDrawable(new OvalShape());
        circle.getPaint().setColor(color);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            userAvatar.setBackground(circle);
        } else {
            userAvatar.setBackgroundDrawable(circle);
        }
    }

    private void applySwatchBackground(Palette.Swatch swatch) {
        int bkg = swatch.getRgb();

        coordinatorLayout.setStatusBarBackgroundColor(bkg);
        collapsingToolbarLayout.setContentScrimColor(bkg);
        appBarLayout.setBackgroundColor(bkg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(bkg);
            getWindow().setNavigationBarColor(bkg);
        }

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (menu != null) {

            menu.clear();

            StoreCredentials settings = new StoreCredentials(this);

            if (user != null && !settings.getUserName().equals(user.login)) {
                if (followingUser) {
                    menu.add(0, R.id.action_menu_unfollow_user, 0, R.string.action_menu_unfollow_user);
                } else {
                    menu.add(0, R.id.action_menu_follow_user, 0, R.string.action_menu_follow_user);
                }

                menu.getItem(0).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == R.id.action_menu_follow_user) {
            followUserAction(new FollowUserClient(this, user.login));
        } else if (item.getItemId() == R.id.action_menu_unfollow_user) {
            followUserAction(new UnfollowUserClient(this, user.login));
        }

        item.setEnabled(false);

        return true;
    }

    private void followUserAction(GithubClient<Boolean> githubClient) {
        githubClient.observable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        followingUser = aBoolean;
                        invalidateOptionsMenu();
                    }
                });
    }

    public void onUserLoaded(final User user) {
        this.user = user;

        userName.setText(user.name);

        StoreCredentials settings = new StoreCredentials(this);

        invalidateOptionsMenu();

        if (updateProfile && selectedAccount != null) {
            AccountManager accountManager = AccountManager.get(this);
            accountManager.setUserData(selectedAccount, AccountsHelper.USER_PIC, user.avatar_url);
            ImageLoader.getInstance().clearMemoryCache();
            ImageLoader.getInstance().clearDiskCache();
        }

        if (!user.login.equalsIgnoreCase(settings.getUserName())) {
            followUserAction(new CheckFollowingUser(this, user.login));
        }

        userResumeFragment.fill(user);
    }

    @Override
    protected void close(boolean navigateUp) {
        if (user != null && updateProfile) {
            Intent intent = new Intent();
            Bundle extras = new Bundle();
            extras.putString(URL_PROFILE, user.avatar_url);
            intent.putExtras(extras);
            setResult(selectedAccount != null ? RESULT_FIRST_USER : RESULT_OK, intent);
        }
        super.close(navigateUp);
    }

    @Override
    public void openRepos(String login) {
        if (viewPager != null && viewPager.getAdapter() != null && viewPager.getAdapter().getCount() >= 1) {
            viewPager.setCurrentItem(1);
        }
    }
}
