use crate::error::Result;
use crate::store::{load_json, save_json};
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingsStore {
    pub values: BTreeMap<String, String>,
    #[serde(skip)]
    root: PathBuf,
}

impl SettingsStore {
    pub fn open(root: &Path) -> Self {
        let mut store: Self = load_json(
            &root.join("settings.json"),
            Self {
                values: BTreeMap::new(),
                root: root.to_path_buf(),
            },
        );
        store.root = root.to_path_buf();
        if store.values.is_empty() {
            store.values.insert("theme".into(), "system".into());
            store.values.insert("dynamicColor".into(), "true".into());
            store.values.insert("reducedMotion".into(), "false".into());
            store.values.insert("gapless".into(), "true".into());
            store.values.insert("audioFocus".into(), "true".into());
        }
        store
    }

    pub fn save(&self) -> Result<()> {
        save_json(&self.root.join("settings.json"), self)
    }

    pub fn set(&mut self, key: &str, value: &str) -> Result<()> {
        self.values.insert(key.to_string(), value.to_string());
        self.save()
    }

    pub fn get(&self, key: &str, default: &str) -> String {
        self.values
            .get(key)
            .cloned()
            .unwrap_or_else(|| default.to_string())
    }
}
