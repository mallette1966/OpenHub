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

import com.thirtydegreesray.dataautoaccess.annotation.AutoAccess;
import com.thirtydegreesray.openhub.common.Event;
import com.thirtydegreesray.openhub.common.SizedMap;
import com.thirtydegreesray.openhub.dao.DaoSession;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.IRepoFilesContract;
import com.thirtydegreesray.openhub.mvp.model.FileModel;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.util.StringUtils;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import javax.inject.Inject;

import retrofit2.Response;
import rx.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/14 16:06:30
 */

public class RepoFilesPresenter extends BasePresenter<IRepoFilesContract.View>
        implements IRepoFilesContract.Presenter{

    private Map<String, ArrayList<FileModel>> cacheMap;
    @AutoAccess Repository repo ;
    @AutoAccess String curPath = "";

    @Inject
    public RepoFilesPresenter(DaoSession daoSession) {
        super(daoSession);
        cacheMap = new SizedMap<>();
        setEventSubscriber(true);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        loadFiles(curPath, false);
    }

    @Override
    public void loadFiles(boolean isReload) {
        loadFiles(curPath, isReload);
    }

    @Override
    public void loadFiles(@NonNull String path, boolean isReload) {
        curPath = path;
        ArrayList<FileModel> filesCache = cacheMap.get(getCacheKey());
        if(!isReload && filesCache != null){
            mView.showFiles(filesCache);
            return ;
        }

        mView.showLoading();
        HttpObserver<ArrayList<FileModel>> httpObserver =
                new HttpObserver<ArrayList<FileModel>>() {
                    @Override
                    public void onError(Throwable error) {
                        mView.showShortToast(error.getMessage());
                        mView.hideLoading();
                    }

                    @Override
                    public void onSuccess(HttpResponse<ArrayList<FileModel>> response) {
                        sort(response.body());
                        cacheMap.put(getCacheKey(), response.body());
                        mView.showFiles(response.body());
                        mView.hideLoading();
                    }
                };
        generalRxHttpExecute(new IObservableCreator<ArrayList<FileModel>>() {
            @Override
            public Observable<Response<ArrayList<FileModel>>> createObservable(boolean forceNetWork) {
                return getRepoService().getRepoFiles(repo.getOwner().getLogin(),
                        repo.getName(), curPath, repo.getDefaultBranch());
            }
        }, httpObserver, false);
    }

    @Override
    public boolean goBack() {
        if(!StringUtils.isBlank(curPath)){
            curPath = curPath.contains("/") ?
                    curPath.substring(0, curPath.lastIndexOf("/")) : "";
            loadFiles(false);
            return true;
        }
        return false;
    }

    private void sort(ArrayList<FileModel> oriList){
        Collections.sort(oriList, new Comparator<FileModel>() {
            @Override
            public int compare(FileModel o1, FileModel o2) {
                if(!o1.getType().equals(o2.getType())){
                    return o1.isDir() ? -1 : 1;
                }
                return 0;
            }
        });
    }

    private String getCacheKey(){
        return repo.getDefaultBranch() + "-" + curPath;
    }

    public String getCurPath() {
        return curPath;
    }

    public void setCurPath(String curPath) {
        this.curPath = curPath;
    }

    @Subscribe
    public void onRepoInfoUpdated(Event.RepoInfoUpdatedEvent event) {
        if (!repo.getFullName().equals(event.repository.getFullName())) return;
        repo = event.repository;
        loadFiles("", false);
    }

}
