import importlib.util
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/extension-repo/newshub_extension_repo.py"
SPEC = importlib.util.spec_from_file_location("newshub_extension_repo", MODULE_PATH)
assert SPEC and SPEC.loader
repo_tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(repo_tool)


class NetworkPolicyCorpusTest(unittest.TestCase):
    def test_valid_policy_matches_canonical_hash(self):
        policy = json.loads((ROOT / "schemas/corpus/valid/network-policy-v2.json").read_text())
        repo_tool.validate_network_policy(policy)
        self.assertEqual(
            "a3fc23c20e8e7361efa26a96d81c52bd13e0fc941cce2de6833ff8ff9dedf347",
            repo_tool.network_policy_hash(policy),
        )

    def test_invalid_policy_is_rejected(self):
        policy = json.loads((ROOT / "schemas/corpus/invalid/network-policy-wildcard.json").read_text())
        with self.assertRaises(repo_tool.RepoError):
            repo_tool.validate_network_policy(policy)

    def test_v3_credentialed_resource_policy_is_canonical_and_bounded(self):
        policy = json.loads((ROOT / "schemas/corpus/valid/network-policy-v3.json").read_text())
        repo_tool.validate_network_policy(policy)
        self.assertEqual(
            "3c6c6b1b85031c873f962b0b53716725972b569c02a1c5d99f70d55035ef9c7a",
            repo_tool.network_policy_hash(policy),
        )

        for mutation in (
            lambda value: value["resource"]["rules"][0].update(userAgent=None),
            lambda value: value["resource"]["rules"][0].update(userAgent="Other/1.0\nInjected"),
            lambda value: value["resource"]["rules"][1].update(userAgent="Unexpected/1.0"),
            lambda value: value["auth"].update(exactHosts=[]),
        ):
            invalid = json.loads(json.dumps(policy))
            mutation(invalid)
            with self.assertRaises(repo_tool.RepoError):
                repo_tool.validate_network_policy(invalid)

    def test_policy_requires_exact_fields(self):
        policy = json.loads((ROOT / "schemas/corpus/valid/network-policy-v2.json").read_text())
        policy["request"]["proxy"] = True
        with self.assertRaises(repo_tool.RepoError):
            repo_tool.validate_network_policy(policy)


class RepositoryConfigCorpusTest(unittest.TestCase):
    def test_valid_config_shape_and_artifact_window(self):
        source = ROOT / "schemas/corpus/valid/repository-config.json"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "repository-config.json"
            config = json.loads(source.read_text())
            path.write_text(json.dumps(config))
            (path.parent / "fixture.apk").write_bytes(b"fixture")
            repo_tool.validate_config(config, path)
            repo_tool.validate_accepted_artifacts(config["extensions"][0]["acceptedArtifacts"], 2, "acceptedArtifacts")

    def test_protocol_v1_config_is_rejected(self):
        config = json.loads((ROOT / "schemas/corpus/invalid/repository-config-protocol-v1.json").read_text())
        with self.assertRaises(repo_tool.RepoError):
            repo_tool.validate_config(config, ROOT / "repository-config.json")

    def test_previous_artifact_must_be_older_and_unique(self):
        digest = "a" * 64
        with self.assertRaises(repo_tool.RepoError):
            repo_tool.validate_accepted_artifacts(
                [
                    {"versionCode": 2, "length": 10, "sha256": digest},
                    {"versionCode": 2, "length": 11, "sha256": "b" * 64},
                ],
                3,
                "acceptedArtifacts",
            )

    def test_uppercase_digest_is_rejected(self):
        with self.assertRaises(repo_tool.RepoError):
            repo_tool.require_sha256("A" * 64, "sha256")

    def test_repository_base_url_requires_canonical_origin(self):
        self.assertEqual(
            "https://extensions.example.com/repo",
            repo_tool.validate_repository_base_url("https://extensions.example.com/repo"),
        )
        for value in (
            "https://user:password@extensions.example.com",
            "https://Extensions.example.com",
            "https://extensions.example.com:444",
            "https://extensions.example.com/repo/../other",
        ):
            with self.subTest(value=value), self.assertRaises(repo_tool.RepoError):
                repo_tool.validate_repository_base_url(value)

    def test_role_key_names_cannot_escape_key_directory(self):
        source = ROOT / "schemas/corpus/valid/repository-config.json"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "repository-config.json"
            config = json.loads(source.read_text())
            config["roles"]["root"]["keyNames"][0] = "../outside"
            (path.parent / "fixture.apk").write_bytes(b"fixture")
            with self.assertRaises(repo_tool.RepoError):
                repo_tool.validate_config(config, path)


class MetadataEnvelopeTest(unittest.TestCase):
    def test_generated_root_meets_threshold_and_rejects_tamper(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config_path = work / "repository-config.json"
            args = type(
                "Args",
                (),
                {
                    "config": config_path,
                    "key_directory": "keys",
                    "public_directory": "public",
                    "mode": "ephemeral",
                    "force": False,
                },
            )()
            repo_tool.command_init(args)
            config = repo_tool.strict_json(config_path)
            root = repo_tool.build_root(config_path, config)
            keys, roles = repo_tool.verify_root(root)
            self.assertEqual(2, roles["root"]["threshold"])
            self.assertEqual(3, len(roles["root"]["keyids"]))
            root["signed"]["version"] = 2
            with self.assertRaises(repo_tool.RepoError):
                repo_tool.verify_envelope(root, keys, roles["root"], "root")

    def test_private_key_permissions_and_output_separation(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config_path = work / "repository-config.json"
            args = type(
                "Args",
                (),
                {
                    "config": config_path,
                    "key_directory": "keys",
                    "public_directory": "public",
                    "mode": "ephemeral",
                    "force": False,
                },
            )()
            repo_tool.command_init(args)
            self.assertEqual(0o700, stat.S_IMODE((work / "keys").stat().st_mode))
            for key in (work / "keys").glob("*.pem"):
                self.assertEqual(0o600, stat.S_IMODE(key.stat().st_mode))
            self.assertFalse((work / "public").is_relative_to(work / "keys"))

    def test_schema_and_envelope_corpus_files_are_structurally_distinct(self):
        for name in (
            "network-policy-v2.schema.json",
            "repository-config.schema.json",
            "signed-metadata-envelope.schema.json",
        ):
            schema = json.loads((ROOT / "schemas" / name).read_text())
            self.assertEqual("https://json-schema.org/draft/2020-12/schema", schema["$schema"])
        valid = json.loads((ROOT / "schemas/corpus/valid/signed-metadata-envelope.json").read_text())
        invalid = json.loads((ROOT / "schemas/corpus/invalid/signed-metadata-envelope-extra-field.json").read_text())
        self.assertEqual({"signatures", "signed"}, set(valid))
        self.assertNotEqual({"signatures", "signed"}, set(invalid))

    def test_root_rotation_meets_old_and_new_thresholds(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config_path = work / "repository-config.json"
            args = type(
                "Args",
                (),
                {
                    "config": config_path,
                    "key_directory": "keys",
                    "public_directory": "public",
                    "mode": "ephemeral",
                    "force": False,
                },
            )()
            repo_tool.command_init(args)
            config = repo_tool.strict_json(config_path)
            old_root = repo_tool.build_root(config_path, config)
            metadata = work / "public/metadata"
            metadata.mkdir(parents=True)
            (metadata / "root.json").write_bytes(repo_tool.canonical_bytes(old_root))
            old_keys, old_roles = repo_tool.verify_root(old_root)

            rotate = type(
                "Args",
                (),
                {"config": config_path, "new_key_name": None, "threshold": None},
            )()
            repo_tool.command_rotate_root(rotate)
            new_root = repo_tool.strict_json(metadata / "2.root.json")
            new_keys, new_roles = repo_tool.parse_root(new_root)
            repo_tool.verify_envelope(new_root, old_keys, old_roles["root"], "root")
            repo_tool.verify_envelope(new_root, new_keys, new_roles["root"], "root")
            self.assertEqual(2, repo_tool.strict_json(config_path)["versions"]["root"])

    def test_root_rotation_refuses_an_existing_next_version(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config_path = work / "repository-config.json"
            init_args = type(
                "Args",
                (),
                {
                    "config": config_path,
                    "key_directory": "keys",
                    "public_directory": "public",
                    "mode": "ephemeral",
                    "force": False,
                },
            )()
            repo_tool.command_init(init_args)
            config = repo_tool.strict_json(config_path)
            metadata = work / "public/metadata"
            metadata.mkdir(parents=True)
            (metadata / "root.json").write_bytes(repo_tool.canonical_bytes(repo_tool.build_root(config_path, config)))
            (metadata / "2.root.json").write_text("reserved")
            rotate_args = type(
                "Args",
                (),
                {"config": config_path, "new_key_name": None, "threshold": None},
            )()
            with self.assertRaises(repo_tool.RepoError):
                repo_tool.command_rotate_root(rotate_args)
            self.assertEqual("reserved", (metadata / "2.root.json").read_text())

    def test_root_rotation_rejects_unsafe_key_name(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config_path = work / "repository-config.json"
            init_args = type(
                "Args",
                (),
                {
                    "config": config_path,
                    "key_directory": "keys",
                    "public_directory": "public",
                    "mode": "ephemeral",
                    "force": False,
                },
            )()
            repo_tool.command_init(init_args)
            config = repo_tool.strict_json(config_path)
            metadata = work / "public/metadata"
            metadata.mkdir(parents=True)
            (metadata / "root.json").write_bytes(repo_tool.canonical_bytes(repo_tool.build_root(config_path, config)))
            rotate_args = type(
                "Args",
                (),
                {"config": config_path, "new_key_name": ["../outside"], "threshold": 1},
            )()
            with self.assertRaises(repo_tool.RepoError):
                repo_tool.command_rotate_root(rotate_args)
            self.assertFalse((work / "outside.pem").exists())

    def test_apk_version_name_requires_a_safe_target_stem(self):
        self.assertIsNotNone(repo_tool.SAFE_STEM_RE.fullmatch("1.2.3-rc_1"))
        self.assertIsNone(repo_tool.SAFE_STEM_RE.fullmatch("../escape"))


class StarterRepositoryIntegrationTest(unittest.TestCase):
    @unittest.skipUnless(os.environ.get("NEWSHUB_STARTER_APK"), "set NEWSHUB_STARTER_APK after building the starter")
    def test_publish_and_validate_starter_apk(self):
        apk = Path(os.environ["NEWSHUB_STARTER_APK"]).resolve()
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config_path = work / "repository-config.json"
            init_args = type(
                "Args",
                (),
                {
                    "config": config_path,
                    "key_directory": "keys",
                    "public_directory": "public",
                    "mode": "ephemeral",
                    "force": False,
                },
            )()
            repo_tool.command_init(init_args)
            inspected = repo_tool.inspect_apk(apk, ROOT / "repository-config.json")
            policy = json.loads((ROOT / "schemas/corpus/valid/network-policy-v2.json").read_text())
            config = repo_tool.strict_json(config_path)
            config["extensions"] = [
                {
                    "apk": str(apk),
                    "name": "Example NewsHub extension",
                    "lang": "en",
                    "apkSignerPins": [inspected["signerDigests"][0]],
                    "lineageRootSha256": inspected["signerDigests"][-1],
                    "sources": [
                        {
                            "id": "com.example.news",
                            "name": "Example News",
                            "lang": "en",
                            "baseUrl": "https://api.example.com",
                            "service": "com.example.newshub.extension.ExampleExtensionService",
                            "protocol": 2,
                            "networkPolicy": policy,
                        }
                    ],
                }
            ]
            config_path.write_bytes(repo_tool.pretty_bytes(config))
            repo_tool.command_publish(type("Args", (), {"config": config_path})())
            repo_tool.validate_repository(work / "public", config_path)


if __name__ == "__main__":
    unittest.main()
