/*
 *    Copyright 2017 ThirtyDegressRay
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.thirtydegreesray.openhub.mvp.presenter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.thirtydegreesray.dataautoaccess.annotation.AutoAccess;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.common.Event;
import com.thirtydegreesray.openhub.dao.DaoSession;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.IRepositoriesContract;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.SearchModel;
import com.thirtydegreesray.openhub.mvp.model.SearchResult;
import com.thirtydegreesray.openhub.ui.fragment.RepositoriesFragment;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;

import javax.inject.Inject;

import retrofit2.Response;
import rx.Observable;

/**
 * Created on 2017/7/18.
 *
 * @author ThirtyDegreesRay
 */

public class RepositoriesPresenter extends BasePresenter<IRepositoriesContract.View>
        implements IRepositoriesContract.Presenter{

    @AutoAccess ArrayList<Repository> repos;

    @AutoAccess RepositoriesFragment.RepositoriesType type;
    @AutoAccess String user;

    @AutoAccess SearchModel searchModel;

    @Inject
    public RepositoriesPresenter(DaoSession daoSession) {
        super(daoSession);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if(type.equals(RepositoriesFragment.RepositoriesType.SEARCH)){
            setEventSubscriber(true);
        }
        if(repos != null) {
            mView.showRepositories(repos);
            return;
        }
        loadRepositories(false, 1);
    }

    @Override
    public void loadRepositories(final boolean isReLoad, final int page) {
        if(type.equals(RepositoriesFragment.RepositoriesType.SEARCH)){
            searchRepos(page);
            return;
        }
        mView.showLoading();
        final boolean readCacheFirst = !isReLoad && page == 1;

        HttpObserver<ArrayList<Repository>> httpObserver = new HttpObserver<ArrayList<Repository>>() {
            @Override
            public void onError(@NonNull Throwable error) {
                mView.hideLoading();
                mView.showLoadError(error.getMessage());
            }

            @Override
            public void onSuccess(@NonNull HttpResponse<ArrayList<Repository>> response) {
                mView.hideLoading();
                if (isReLoad || readCacheFirst || repos == null) {
                    repos = response.body();
                }else{
                    repos.addAll(response.body());
                }
                mView.showRepositories(repos);
            }
        };

        generalRxHttpExecute(new IObservableCreator<ArrayList<Repository>>() {
            @Nullable
            @Override
            public Observable<Response<ArrayList<Repository>>> createObservable(boolean forceNetWork) {
                return getObservable(forceNetWork, page);
            }
        }, httpObserver, readCacheFirst);

    }

    private Observable<Response<ArrayList<Repository>>> getObservable(boolean forceNetWork, int page){
        String loginedUser = AppData.INSTANCE.getLoggedUser().getLogin();
        switch (type){
            case OWNED:
                return getRepoService().getUserRepos(forceNetWork, user, page);
            case STARRED:
                return getRepoService().getStarredRepos(forceNetWork, user, page);
            default:
                return null;
        }
    }

    private void searchRepos(final int page){
        mView.showLoading();

        HttpObserver<SearchResult<Repository>> httpObserver =
                new HttpObserver<SearchResult<Repository>>() {
            @Override
            public void onError(@NonNull Throwable error) {
                mView.hideLoading();
                mView.showLoadError(error.getMessage());
            }

            @Override
            public void onSuccess(@NonNull HttpResponse<SearchResult<Repository>> response) {
                mView.hideLoading();
                if (repos == null || page == 1) {
                    repos = response.body().getItems();
                }else{
                    repos.addAll(response.body().getItems());
                }
                mView.showRepositories(repos);
            }
        };
        generalRxHttpExecute(new IObservableCreator<SearchResult<Repository>>() {
            @Nullable
            @Override
            public Observable<Response<SearchResult<Repository>>> createObservable(boolean forceNetWork) {
                return getSearchService().searchRepos(searchModel.getQuery(), searchModel.getSort(),
                        searchModel.getOrder(), page);
            }
        }, httpObserver);
    }

    @Subscribe
    public void onSearchEvent(@NonNull Event.SearchEvent searchEvent){
        if (!searchEvent.searchModel.getType().equals(SearchModel.SearchType.Repository)) return;
        this.searchModel = searchEvent.searchModel;
        searchRepos(1);
    }

}
