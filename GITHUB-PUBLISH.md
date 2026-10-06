# Publish ColonyClash to GitHub

Target repository: `https://github.com/ostaddehdari/ColonyClash`

From the extracted project root on a server that already has GitHub push credentials:

```bash
bash scripts/push-to-ostaddehdari-github.sh
```

The script refuses to push `.env`, keystores, or `google-services.json` if found in the project tree.
It creates/pushes `main` and tags the baseline as `v0.8.1`.

After push, GitHub Actions workflow `.github/workflows/ci-build.yml` builds backend TypeScript and the three debug APK flavors.
