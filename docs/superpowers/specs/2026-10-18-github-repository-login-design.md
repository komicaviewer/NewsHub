# GitHub 登入與 extensions repository 選取設計

日期：2026-10-18。狀態：設計文件及實作計畫已經使用者核准，採 Native 執行。

## 目的與既有狀態

在管理來源加入「登入 GitHub」，讓使用者選取帳號可讀取的個人與組織 repositories，驗證 NewsHub extensions 格式並確認根金鑰後加入。保留公開 HTTPS 與手動 GitHub private／PAT 流程。

目前 ManageReposScreen／ManageReposViewModel 只有公開 HTTPS 與 GitHub Contents + PAT。Marketplace 已有根金鑰預覽與確認、受限的 GitHub Contents transport；RepositoryCredentialVault 提供 Android Keystore 保護。現有論壇 OAuth 與 SourceIdentity 綁定，不直接套用為 GitHub 來源登入。

設計前唯讀確認 twkevinzhang 個人及 komicaviewer 組織沒有已註冊 OAuth App，專案未找到 GitHub OAuth Client ID。使用者核准後已在 komicaviewer 建立 NewsHub 專用 OAuth App `3942641`；維護設定見 `docs/github-repository-login.md`。

## 已核准選擇

- 使用 OAuth App 的較廣 repo 授權，列出帳號可讀取的 repositories，而非 GitHub App 的選定 repository 安裝授權。GitHub OAuth 的 repo scope 同時包含寫入權限；NewsHub 功能僅發出必要的讀取請求與登入協議請求。
- Device Flow，不在 APK 放 client secret、不新增登入後端。
- 同時一個 GitHub 帳號，支援登出與重新登入。
- 顯示所有目前授權可列出的 repo，搜尋與分頁；選取後才檢查 extensions metadata。
- 優先使用 API 回傳的 default_branch 與根目錄；檢查失敗可修改分支、子目錄後重試。
- 登出後保留來源及已安裝 extensions，登入來源停止更新；重新登入後確認讀取權限，有權限者自動恢復。
- HTTPS 與手動 PAT 來源不受 GitHub 帳號登出影響。

## 介面與資料流

```text
管理來源
  [公開 HTTPS] [GitHub private] [登入 GitHub]
                                  |
                        授權碼 + 開啟 GitHub + 取消
                                  |
                        帳號資訊 + 登出
                        搜尋 repo / 結果 / 載入更多
                                  |
                        選取 repo
                        預設分支 + 根目錄（可調整）
                                  |
                        檢查 signed metadata
                                  |
                        根金鑰指紋確認 → 加入來源
```

登入畫面顯示 GitHub 提供的 verification URI、user code 與到期狀態。由系統瀏覽器開啟官方 GitHub 授權頁。使用者取消、離開登入流程或碼到期即停止輪詢；不將 device code 或 token 顯示於錯誤、日誌或 analytics。

清單顯示 owner/name、private 標示與必要描述，讓使用者辨識 repo。搜尋只涵蓋已授權帳號可列出的 repo，不以公開全站搜尋替代。以分頁的帳號可存取清單建立目前 session 的搜尋目錄；有搜尋字詞時逐頁繼續比對並顯示搜尋進度，尚未讀完不得宣稱沒有結果。限制請求頻率並支援取消；rate limit 時保留結果、依 GitHub 回應提供稍後重試。切換 query 不重複並行掃描同一帳號目錄；登出清除清單與搜尋狀態。

「所有」指 GitHub API 在本次 OAuth 授權與組織政策下允許列出的清單；組織封鎖 OAuth／要求 SSO 或管理員批准時，不保證列出使用者在網頁可見的全部 repo，介面需說明授權限制。

選取結果使用 API 的 repo identity 與 default_branch，不假設 main。子目錄是 repository 內的相對路徑，拒絕 traversal、外部 origin 與不合法編碼。仍要求現有 metadata/root.json 等安全格式，無法用任意一般 GitHub repo 取代 extensions repo。根檢查失敗不保存新來源；更換分支／路徑後舊預覽失效。相同標準化來源重複加入時提示已存在，不靜默替換原有 PAT 或信任設定。

## 元件與憑證邊界

- GitHub OAuth client：Device Flow 啟動、有限輪詢、登入回應與錯誤解析；僅允許固定官方 HTTPS endpoints。
- GitHub account session：單一帳號 identity、加密持久化 token、登出及 session generation。應用程式重啟後可恢復登入，但不恢復過期的登入碼輪詢。
- GitHub repository catalog：驗證目前帳號、列出可存取 repo、分頁及搜尋；不處理根金鑰信任。
- Marketplace access binding：來源標記為 public、manual PAT 或 GitHub account credential 來源。OAuth 與 PAT 可沿用 GitHub Contents transport，但不可只憑 transport kind 推斷憑證歸屬。
- ManageRepos UI／ViewModel：顯示登入、repo 選取、分支／路徑與既有信任確認流程；網路協議與 token 保存不放在 Compose 元件。

OAuth token 在帳號 vault 集中保存，不複製成每個 repo 的長期 PAT。來源只保存 credential binding 與必要 repository descriptor。沿用 Keystore 保護；token 不寫入一般 DataStore、備份、URL、UI saved state 或日誌。既有 PAT schema 的遷移預設為 manual PAT，不隱式改綁帳號。

所有 repository metadata 與 APK 讀取都須在取得憑證時檢查 session 及來源資格；不只在畫面按鈕阻擋。登出遞增 generation、取消該 session 的請求並刪除 token，避免較早完成的登入／讀取回應再次保存憑證或提交更新。登出期間未完成下載不應完成安裝流程；已安裝 extensions 可繼續運作。

## 暫停與自動恢復

登入資格與使用者既有 ACTIVE／SUSPENDED／REVOKED 信任狀態分開。登出使 account-bound 來源因缺憑證暫停更新；不把使用者手動停用或撤銷的來源改回 ACTIVE。

重新登入先透過 GitHub 帳號 API 驗證 token 與 identity，再逐一對 account-bound 來源檢查指定 repo、分支與目錄的讀取能力。可讀者解除登入資格阻擋，仍遵守其原有信任狀態；不可讀者保留並顯示原因。切換帳號亦採此規則，只依目前帳號實際權限恢復，不將舊帳號 token 沿用給新帳號。

網路逾時或 rate limit 不是權限撤銷證據：保持待確認並允許重試。401 顯示重新登入；403 區分 rate limit／組織政策等可辨識情況；404 顯示不存在或無權存取，不虛構確定原因。金鑰輪替及新增金鑰維持原有驗證／人工信任規則，登入不構成金鑰批准。

## 外部設定與錯誤處理

OAuth App 歸 komicaviewer，名稱 NewsHub，啟用 Device Flow；homepage 指向專案公開頁面。註冊必填的 callback URL 依 GitHub 當下規則設定，Device Flow 不依賴 callback 或新後端。Client ID 是公開設定；若尚未配置則介面明確顯示登入尚未設定，不顯示無法完成的登入流程。

依官方回應的 interval 輪詢；authorization_pending 繼續、slow_down 降頻、access_denied／expired_token 結束。不無限重試，不超過授權碼到期時間；網路請求各有 timeout。登出明確指移除此裝置的登入憑證，不宣稱已撤銷 GitHub 網站上的整個 OAuth grant；提供 GitHub 授權管理頁入口。若 GitHub 回傳 token 到期資訊，依支援協議處理；不能以儲存 client secret 作為更新權杖的捷徑，無法安全延續時要求重新登入。

不建立 Cloud Run、付費服務、網站後端或定期雲端工作。外部 OAuth App 註冊不包含更改其他 Apps、授權或既有 repo 權限。

## 驗收與不包含項目

必要測試：Device Flow 成功／取消／過期／slow_down／拒絕、session 重啟及登出競態、完整清單跨頁搜尋與 rate limit、非 main 分支／子目錄、重複加入、無效格式不保存、根金鑰仍需確認、PAT／HTTPS 不受登出影響、account-bound 更新與下載在登出時停止、重新登入恢復權限及手動停用／撤銷不被復原、憑證不洩漏。

以 local fake HTTP／unit tests 為主，Android UI 驗證登入狀態與選取流程；實際 OAuth smoke 使用註冊完成後的 Client ID，授權帳號登入必須由使用者本人完成，不由 agent 代按授權。交付記錄哪些檢查是真實 GitHub、哪些為模擬。

不包含多帳號、任意 GitHub Enterprise host、自動尋找所有子目錄、寫入 repository、靜默變更 PAT 來源、發布 app、部署後端或發布 extensions。

## 查證參考

- https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps
- https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps
- https://docs.github.com/en/rest/repos/repos#list-repositories-for-the-authenticated-user
- https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app

上述協議在前述對話查證；實作前仍需核對當下回應及支援參數。repo 搜尋採帳號可存取目錄分頁比對，是為避免全站搜尋與私有 repo 權限限制造成漏列的設計選擇。
