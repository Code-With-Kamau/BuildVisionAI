package com.buildvisionai.repository;

/** Result of background work, delivered on the main thread. */
public interface RepoCallback<T> {
    void onSuccess(T value);

    void onError(Exception error);
}
