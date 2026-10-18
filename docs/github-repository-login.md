# GitHub 登入與 extensions 來源

管理來源有三種加入方式：公開 HTTPS、手動 GitHub private／PAT，以及登入 GitHub。登入 GitHub 同時支援一個帳號，可搜尋本次授權允許讀取的個人與組織 repositories。

## 使用流程

1. 在「管理來源」選「新增來源」→「GitHub 帳號」。未登入時選「繼續連接 GitHub」，取得授權碼並開啟 GitHub 官方授權頁，由使用者本人輸入授權碼並確認授權；已登入直接進入儲存庫清單。
2. 回到 NewsHub，搜尋或分頁瀏覽 repositories。搜尋會繼續讀取後續頁面；清單完整前不會宣稱沒有結果。
3. 選取 repo 後進入獨立「檢查來源」頁，預設使用 API 回傳的預設分支與根目錄；需要時展開「進階設定」修改分支／revision 和子目錄。返回清單保留搜尋與捲動位置。
4. 檢查來源，從發布者可信管道核對根金鑰指紋，再確認加入。

一般 GitHub repo 不一定是 extensions 來源；仍需 `metadata/root.json` 等 NewsHub 簽署 metadata。相同 repo 已加入時不會覆蓋既有來源或 PAT。

GitHub OAuth 的 `repo` scope 包含寫入權限，無法只以此 scope 授予私有 repo 讀取；這是採用較廣清單方案的取捨。NewsHub 此功能只讀取 repo，登入協議本身使用必要的 POST。組織的 OAuth／SSO 政策可能要求管理員核准，清單只包含當下 API 允許列出的 repo。

帳號及搜尋區固定在儲存庫清單頂部，帳號選單提供「登出 GitHub」與「管理 GitHub 授權」。

## 登出與更新

登出會清除此裝置的 GitHub token，取消登入來源的請求、確認與尚未交接的下載；來源紀錄及已安裝 extensions 保留。公開 HTTPS 與手動 PAT 來源不受影響。

重新登入後會逐一確認登入來源的讀取資格與既有信任；成功者恢復更新，無權或暫時無法確認者維持待確認。使用者手動停用／撤銷的來源不會自動恢復。重新登入不代表接受新根金鑰。

App 不會在登出時撤銷 GitHub 網站上的整個 OAuth 授權。需要全面撤銷時使用 [GitHub 授權管理](https://github.com/settings/applications)。目前 token 到期後要求重新登入，不保存 refresh token；不在 APK 保存 client secret。

## 維護設定

- OAuth App：NewsHub，owner `komicaviewer`，App ID `3942641`。
- [App 管理頁](https://github.com/organizations/komicaviewer/settings/applications/3942641)。
- 公開 Client ID：`Ov23li7ka2rPsx0YetVc`，已作為組建預設值。
- Homepage／callback：`https://github.com/komicaviewer/NewsHub`；Device Flow 不使用 callback。
- Device Flow 已啟用；保留 GitHub 預設的 access token 到期設定，未建立 client secret。
- 自有 fork 可用 Gradle property 或環境變數 `GITHUB_OAUTH_CLIENT_ID` 覆寫；傳空字串時介面顯示尚未設定。
- token 集中保存在 `GitHubAccountVault`，由 Android Keystore 加密，既有 backup rules 排除 shared preferences。metadata 與 APK 使用不含 debug URL logger 的獨立 client。

不需部署後端、Cloud Run 或其他付費服務。此功能不會自動發布 repo、APK 或 extensions。
