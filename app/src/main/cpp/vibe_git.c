/*
 * Minimal JNI bridge over vendored libgit2 for the version-snapshot spike (issue #36).
 * Prototype scope only: open-or-init with an external git-dir, `git add -A`-style
 * commit, recent log, and `git reset --hard` restore. Not wired to the data layer.
 */
#include <jni.h>
#include <git2.h>
#include <git2/sys/errors.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

static void ensure_init(void) {
    static int initialized = 0;
    if (!initialized) {
        git_libgit2_init();
        initialized = 1;
    }
}

static void throw_last_error(JNIEnv *env, const char *fallback) {
    const git_error *error = git_error_last();
    char message[512];
    snprintf(message, sizeof(message), "%s: %s", fallback,
             (error && error->message) ? error->message : "unknown libgit2 error");
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

static git_repository *handle_to_repo(jlong handle) {
    return (git_repository *)(intptr_t)handle;
}

JNIEXPORT jstring JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_version(JNIEnv *env, jobject thiz) {
    ensure_init();
    int major = 0;
    int minor = 0;
    int rev = 0;
    git_libgit2_version(&major, &minor, &rev);
    char buffer[32];
    snprintf(buffer, sizeof(buffer), "%d.%d.%d", major, minor, rev);
    return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jlong JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_openOrInit(JNIEnv *env, jobject thiz,
                                                                 jstring work_dir, jstring git_dir) {
    ensure_init();
    const char *work = (*env)->GetStringUTFChars(env, work_dir, NULL);
    const char *gitd = (*env)->GetStringUTFChars(env, git_dir, NULL);
    if (work == NULL || gitd == NULL) {
        if (work != NULL) (*env)->ReleaseStringUTFChars(env, work_dir, work);
        if (gitd != NULL) (*env)->ReleaseStringUTFChars(env, git_dir, gitd);
        return 0;
    }

    git_repository *repo = NULL;
    git_repository_init_options options = GIT_REPOSITORY_INIT_OPTIONS_INIT;
    // 外置 git-dir 布局：git-dir 直接使用传入的独立目录（versions.git），配合 workdir_path
    // 指向工作区；不设 NO_DOTGIT_DIR 的话 libgit2 会再追加 /.git 造成双层嵌套。
    options.flags = GIT_REPOSITORY_INIT_MKPATH | GIT_REPOSITORY_INIT_NO_DOTGIT_DIR;
    options.workdir_path = work;
    options.initial_head = "master";
    int error = git_repository_init_ext(&repo, gitd, &options);

    if (error == 0) {
        // 我们总是以显式 workdir+gitdir 打开仓库，工作区的 .git 链接文件没有用处，
        // 删掉它以保持工作区纯静态文件（预览服务器不会暴露 .git）。
        char link_path[4096];
        snprintf(link_path, sizeof(link_path), "%s/.git", work);
        remove(link_path);
    }

    (*env)->ReleaseStringUTFChars(env, work_dir, work);
    (*env)->ReleaseStringUTFChars(env, git_dir, gitd);

    if (error < 0) {
        throw_last_error(env, "git init failed");
        return 0;
    }
    return (jlong)(intptr_t)repo;
}

JNIEXPORT jstring JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_commitAll(JNIEnv *env, jobject thiz,
                                                                jlong handle, jstring message) {
    ensure_init();
    git_repository *repo = handle_to_repo(handle);
    const char *msg = (*env)->GetStringUTFChars(env, message, NULL);
    if (msg == NULL) {
        return NULL;
    }

    int error = 0;
    jstring result = NULL;
    git_index *index = NULL;
    git_tree *tree = NULL;
    git_commit *parent = NULL;
    git_signature *signature = NULL;
    git_oid commit_id;
    char hex[GIT_OID_MAX_HEXSIZE + 1];

    if ((error = git_repository_index(&index, repo)) < 0) goto done;
    if ((error = git_index_add_all(index, NULL, GIT_INDEX_ADD_DEFAULT, NULL, NULL)) < 0) goto done;
    if ((error = git_index_update_all(index, NULL, NULL, NULL)) < 0) goto done;
    if ((error = git_index_write(index)) < 0) goto done;

    git_oid tree_id;
    if ((error = git_index_write_tree(&tree_id, index)) < 0) goto done;
    if ((error = git_tree_lookup(&tree, repo, &tree_id)) < 0) goto done;
    if ((error = git_signature_now(&signature, "Vibe Design", "vibe-design@local.app")) < 0) goto done;

    size_t parent_count = 0;
    const git_commit *parents[1] = {NULL};
    git_reference *head_ref = NULL;
    if (git_repository_head(&head_ref, repo) == 0) {
        // git_repository_head 的出参是 git_reference（不是 git_commit），必须经
        // git_reference_target + git_commit_lookup 转成真正的提交对象再作父提交。
        const git_oid *head_id = git_reference_target(head_ref);
        if (head_id != NULL && git_commit_lookup(&parent, repo, head_id) == 0) {
            parents[0] = parent;
            parent_count = 1;
        }
        git_reference_free(head_ref);
    } else {
        git_error_clear();
    }

    error = git_commit_create(&commit_id, repo, "HEAD", signature, signature, NULL, msg, tree,
                              parent_count, parents);
    if (error < 0) goto done;

    git_oid_tostr(hex, sizeof(hex), &commit_id);
    result = (*env)->NewStringUTF(env, hex);

done:
    if (signature != NULL) git_signature_free(signature);
    if (parent != NULL) git_commit_free(parent);
    if (tree != NULL) git_tree_free(tree);
    if (index != NULL) git_index_free(index);
    (*env)->ReleaseStringUTFChars(env, message, msg);
    if (result == NULL) {
        throw_last_error(env, "git commit failed");
        return NULL;
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_log(JNIEnv *env, jobject thiz, jlong handle,
                                                          jint limit) {
    ensure_init();
    git_repository *repo = handle_to_repo(handle);
    jclass string_class = (*env)->FindClass(env, "java/lang/String");

    int error = 0;
    git_revwalk *walk = NULL;
    git_commit *commit = NULL;
    jobjectArray array = (*env)->NewObjectArray(env, limit, string_class, NULL);
    if (array == NULL) return NULL;
    jsize count = 0;

    if (git_repository_head_unborn(repo) == 1) {
        jobjectArray empty = (*env)->NewObjectArray(env, 0, string_class, NULL);
        return empty;
    }

    if ((error = git_revwalk_new(&walk, repo)) < 0) goto done;
    // 时间精度只到秒，同秒内的连续提交需要拓扑序兜底（子提交必在父提交之前）。
    git_revwalk_sorting(walk, GIT_SORT_TOPOLOGICAL | GIT_SORT_TIME);
    if ((error = git_revwalk_push_head(walk)) < 0) goto done;

    git_oid oid;
    while (count < limit && git_revwalk_next(&oid, walk) == 0) {
        if (git_commit_lookup(&commit, repo, &oid) < 0) goto done;
        char hex[GIT_OID_MAX_HEXSIZE + 1];
        git_oid_tostr(hex, sizeof(hex), &oid);
        const char *summary = git_commit_summary(commit);
        char line[1024];
        snprintf(line, sizeof(line), "%s\t%lld\t%s", hex, (long long)git_commit_time(commit),
                 summary ? summary : "");
        jstring element = (*env)->NewStringUTF(env, line);
        (*env)->SetObjectArrayElement(env, array, count, element);
        (*env)->DeleteLocalRef(env, element);
        git_commit_free(commit);
        commit = NULL;
        count++;
    }

    // 成功路径同样要释放 revwalk,否则频繁进出版本页会持续泄漏 native 内存。
    git_revwalk_free(walk);
    walk = NULL;

    if (count == limit) {
        return array;
    }
    jobjectArray trimmed = (*env)->NewObjectArray(env, count, string_class, NULL);
    for (jsize i = 0; i < count; i++) {
        jstring element = (jstring)(*env)->GetObjectArrayElement(env, array, i);
        (*env)->SetObjectArrayElement(env, trimmed, i, element);
        (*env)->DeleteLocalRef(env, element);
    }
    return trimmed;

done:
    if (commit != NULL) git_commit_free(commit);
    if (walk != NULL) git_revwalk_free(walk);
    throw_last_error(env, "git log failed");
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_checkoutTree(JNIEnv *env, jobject thiz,
                                                             jlong handle, jstring oid_hex,
                                                             jstring target_dir) {
    ensure_init();
    git_repository *repo = handle_to_repo(handle);
    const char *hex = (*env)->GetStringUTFChars(env, oid_hex, NULL);
    const char *dir = target_dir == NULL ? NULL : (*env)->GetStringUTFChars(env, target_dir, NULL);
    if (hex == NULL || (target_dir != NULL && dir == NULL)) {
        if (hex != NULL) (*env)->ReleaseStringUTFChars(env, oid_hex, hex);
        if (dir != NULL) (*env)->ReleaseStringUTFChars(env, target_dir, dir);
        return;
    }

    git_object *target = NULL;
    git_object *tree = NULL;
    int error = git_revparse_single(&target, repo, hex);
    if (error == 0) {
        error = git_object_peel(&tree, target, GIT_OBJECT_TREE);
    }
    if (error == 0) {
        git_checkout_options options = GIT_CHECKOUT_OPTIONS_INIT;
        options.checkout_strategy = GIT_CHECKOUT_FORCE;
        // 恢复绝不逐文件直写工作区:完整检出目标版本到独立目录,由 Kotlin 层用项目
        // 既有的 pending + 原子移动模式整体替换,进程中途被杀不会留下半恢复工作区。
        options.target_directory = dir;
        error = git_checkout_tree(repo, tree, &options);
    }
    git_object_free(tree);
    git_object_free(target);
    (*env)->ReleaseStringUTFChars(env, oid_hex, hex);
    if (dir != NULL) (*env)->ReleaseStringUTFChars(env, target_dir, dir);

    if (error < 0) {
        throw_last_error(env, "git checkout failed");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_isDirty(JNIEnv *env, jobject thiz, jlong handle) {
    ensure_init();
    git_repository *repo = handle_to_repo(handle);
    // 显式排除 ignored:它们不参与快照,不应触发恢复前的保护性提交。
    git_status_options options = GIT_STATUS_OPTIONS_INIT;
    options.show = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    options.flags = GIT_STATUS_OPT_INCLUDE_UNTRACKED | GIT_STATUS_OPT_RECURSE_UNTRACKED_DIRS;

    git_status_list *status = NULL;
    if (git_status_list_new(&status, repo, &options) < 0) {
        throw_last_error(env, "git status failed");
        return JNI_FALSE;
    }
    jboolean dirty = git_status_list_entrycount(status) > 0 ? JNI_TRUE : JNI_FALSE;
    git_status_list_free(status);
    return dirty;
}

JNIEXPORT void JNICALL
Java_com_aeibi_design_data_versions_git_Libgit2_close(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle != 0) {
        git_repository_free(handle_to_repo(handle));
    }
}
