/**
 * KormoKari Android Permission Engine & Contextual Bridge Facade
 * Phase 2 — Worker Online Pre-Flight & Permission UI Automation
 *
 * Package: com.kormokari.app
 * Canonical Origin: https://kormokari.com
 * Compatibility: Android WebView Native Bridge, TWA postMessage, and Standard Mobile Browsers.
 */

(function (window, document) {
    'use strict';

    // Prevent duplicate initialization
    if (window.KormoKariPermissionEngineInitialized) {
        return;
    }
    window.KormoKariPermissionEngineInitialized = true;

    // =========================================================================
    // 1. CONSTANTS & CANONICAL PERMISSION DEFINITIONS
    // =========================================================================

    const PERMISSION_KEYS = {
        LOCATION_FOREGROUND: 'ACCESS_FINE_LOCATION',
        LOCATION_BACKGROUND: 'ACCESS_BACKGROUND_LOCATION',
        NOTIFICATIONS: 'POST_NOTIFICATIONS',
        CAMERA: 'CAMERA',
        MICROPHONE: 'RECORD_AUDIO'
    };

    const STATUS_CODES = {
        GRANTED: 'GRANTED',
        DENIED: 'DENIED',
        PERMANENTLY_DENIED: 'PERMANENTLY_DENIED',
        GPS_DISABLED: 'GPS_DISABLED',
        SETTINGS_REQUIRED: 'SETTINGS_REQUIRED',
        NOT_AVAILABLE_IN_BROWSER: 'NOT_AVAILABLE_IN_BROWSER',
        BRIDGE_UNAVAILABLE: 'BRIDGE_UNAVAILABLE'
    };

    const TRACKING_STATUS = {
        STOPPED: 'STOPPED',
        STARTING: 'STARTING',
        ACTIVE: 'ACTIVE',
        ERROR: 'ERROR'
    };

    // =========================================================================
    // 2. NATIVE BRIDGE FACADE & POLYFILL
    // =========================================================================

    /**
     * Resolves the active native Android bridge instance if exposed in the WebView.
     */
    function getRawBridge() {
        if (typeof window.KormoKariAndroidPermissionsBridge !== 'undefined' && window.KormoKariAndroidPermissionsBridge !== null) {
            return window.KormoKariAndroidPermissionsBridge;
        }
        if (typeof window.AndroidPermissionsBridge !== 'undefined' && window.AndroidPermissionsBridge !== null) {
            return window.AndroidPermissionsBridge;
        }
        return null;
    }

    /**
     * Public facade exposing canonical methods required by KormoKari.
     */
    const PermissionsFacade = {
        PERMISSION_KEYS: PERMISSION_KEYS,
        STATUS_CODES: STATUS_CODES,
        TRACKING_STATUS: TRACKING_STATUS,

        /**
         * Checks whether the native Android permission bridge is active in this execution context.
         */
        isNativeBridgeAvailable: function () {
            const raw = getRawBridge();
            return !!(raw && typeof raw.getPermissionStatus === 'function');
        },

        /**
         * Returns the underlying raw bridge or null.
         */
        getRawBridge: function () {
            return getRawBridge();
        },

        /**
         * Queries the real-time status of an individual permission.
         * @param {string} key Permission key (e.g. 'ACCESS_FINE_LOCATION')
         * @returns {string} Status code
         */
        getPermissionStatus: function (key) {
            const raw = getRawBridge();
            if (raw && typeof raw.getPermissionStatus === 'function') {
                try {
                    return String(raw.getPermissionStatus(key) || STATUS_CODES.DENIED);
                } catch (e) {
                    console.error('[KormoKariPermissions] Failed to query permission:', key, e);
                    return STATUS_CODES.DENIED;
                }
            }

            // Fallback for regular mobile browsers
            return STATUS_CODES.NOT_AVAILABLE_IN_BROWSER;
        },

        /**
         * Queries all permission statuses at once.
         * @returns {Object} Mapping of permission keys to statuses
         */
        getAllPermissionStatuses: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.getAllPermissionStatuses === 'function') {
                try {
                    const rawJson = raw.getAllPermissionStatuses();
                    return typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson;
                } catch (e) {
                    console.error('[KormoKariPermissions] Failed to parse all statuses:', e);
                }
            }

            const statuses = {};
            statuses[PERMISSION_KEYS.LOCATION_FOREGROUND] = this.getPermissionStatus(PERMISSION_KEYS.LOCATION_FOREGROUND);
            statuses[PERMISSION_KEYS.LOCATION_BACKGROUND] = this.getPermissionStatus(PERMISSION_KEYS.LOCATION_BACKGROUND);
            statuses[PERMISSION_KEYS.NOTIFICATIONS] = this.getPermissionStatus(PERMISSION_KEYS.NOTIFICATIONS);
            statuses[PERMISSION_KEYS.CAMERA] = this.getPermissionStatus(PERMISSION_KEYS.CAMERA);
            statuses[PERMISSION_KEYS.MICROPHONE] = this.getPermissionStatus(PERMISSION_KEYS.MICROPHONE);
            return statuses;
        },

        /**
         * Requests a runtime permission via the native Android permission dispatcher.
         * @param {string} key Permission key
         * @returns {Promise<string>} Resolves with the new status
         */
        requestRuntimePermission: function (key) {
            return new Promise((resolve) => {
                const raw = getRawBridge();
                if (!raw || typeof raw.requestRuntimePermission !== 'function') {
                    // Browser fallback: attempt standard Web APIs where possible
                    return fallbackBrowserPermissionRequest(key).then(resolve);
                }

                // If bridge supports direct synchronous or callback-based request
                const callbackId = 'perm_cb_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
                window[callbackId] = function (resultStatus) {
                    delete window[callbackId];
                    resolve(String(resultStatus || STATUS_CODES.DENIED));
                };

                try {
                    if (raw.requestRuntimePermissionWithCallback) {
                        raw.requestRuntimePermissionWithCallback(key, callbackId);
                    } else {
                        const status = raw.requestRuntimePermission(key);
                        resolve(String(status || STATUS_CODES.DENIED));
                    }
                } catch (e) {
                    console.error('[KormoKariPermissions] Request exception:', key, e);
                    resolve(STATUS_CODES.DENIED);
                }
            });
        },

        /**
         * Verifies whether device location providers (GPS / Network location) are enabled in system settings.
         */
        isLocationServiceEnabled: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.isLocationServiceEnabled === 'function') {
                try {
                    return Boolean(raw.isLocationServiceEnabled());
                } catch (e) {
                    console.error('[KormoKariPermissions] isLocationServiceEnabled failed:', e);
                    return false;
                }
            }
            // In standard browsers, assume true if permission is granted, otherwise unknown
            return true;
        },

        /**
         * Checks whether worker background GPS tracking is currently running.
         */
        isTrackingActive: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.isTrackingActive === 'function') {
                try {
                    return Boolean(raw.isTrackingActive());
                } catch (e) {
                    return false;
                }
            }
            return false;
        },

        /**
         * Queries the native tracking service state and telemetry details.
         */
        getTrackingStatus: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.getTrackingStatus === 'function') {
                try {
                    const status = raw.getTrackingStatus();
                    return typeof status === 'string' ? JSON.parse(status) : status;
                } catch (e) {
                    return { status: TRACKING_STATUS.STOPPED, error: String(e) };
                }
            }
            return {
                status: this.isTrackingActive() ? TRACKING_STATUS.ACTIVE : TRACKING_STATUS.STOPPED,
                isNative: false
            };
        },

        /**
         * Starts the native worker background tracking service.
         * @param {string} authToken Session authorization token
         * @returns {Promise<boolean>}
         */
        startTracking: function (authToken) {
            return new Promise((resolve) => {
                const raw = getRawBridge();
                if (raw && typeof raw.startTracking === 'function') {
                    try {
                        const success = raw.startTracking(authToken || '');
                        resolve(Boolean(success));
                        return;
                    } catch (e) {
                        console.error('[KormoKariPermissions] startTracking failed:', e);
                        resolve(false);
                        return;
                    }
                }
                // Browser context cannot run native background location service
                resolve(false);
            });
        },

        /**
         * Stops the native worker tracking service.
         */
        stopTracking: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.stopTracking === 'function') {
                try {
                    return Boolean(raw.stopTracking());
                } catch (e) {
                    return false;
                }
            }
            return true;
        },

        /**
         * Opens the device Application Details settings screen for KormoKari.
         */
        openAppSettings: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.openAppSettings === 'function') {
                raw.openAppSettings();
            } else {
                alert('Please open your Android Device Settings > Apps > KormoKari > Permissions to update permissions.');
            }
        },

        /**
         * Opens the device Notification Settings screen.
         */
        openNotificationSettings: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.openNotificationSettings === 'function') {
                raw.openNotificationSettings();
            } else {
                this.openAppSettings();
            }
        },

        /**
         * Opens the device Location / GPS Settings screen.
         */
        openLocationSettings: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.openLocationSettings === 'function') {
                raw.openLocationSettings();
            } else {
                alert('Please open your Android Settings > Location to turn on GPS.');
            }
        },

        /**
         * Opens battery optimization settings for KormoKari.
         */
        openBatteryOptimizationSettings: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.openBatteryOptimizationSettings === 'function') {
                raw.openBatteryOptimizationSettings();
            } else {
                this.openAppSettings();
            }
        },

        /**
         * Prompts the system dialog to ignore battery optimizations for background tracking.
         */
        requestBatteryOptimizationExemption: function () {
            const raw = getRawBridge();
            if (raw && typeof raw.requestBatteryOptimizationExemption === 'function') {
                try {
                    return Boolean(raw.requestBatteryOptimizationExemption());
                } catch (e) {
                    return false;
                }
            }
            return false;
        }
    };

    // Fallback for desktop/mobile browsers using standard HTML5 APIs
    function fallbackBrowserPermissionRequest(key) {
        return new Promise((resolve) => {
            if (key === PERMISSION_KEYS.LOCATION_FOREGROUND) {
                if (!navigator.geolocation) {
                    return resolve(STATUS_CODES.DENIED);
                }
                navigator.geolocation.getCurrentPosition(
                    function () { resolve(STATUS_CODES.GRANTED); },
                    function (err) {
                        if (err.code === err.PERMISSION_DENIED) {
                            resolve(STATUS_CODES.PERMANENTLY_DENIED);
                        } else {
                            resolve(STATUS_CODES.DENIED);
                        }
                    },
                    { timeout: 8000, enableHighAccuracy: true }
                );
            } else if (key === PERMISSION_KEYS.MICROPHONE) {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    return resolve(STATUS_CODES.NOT_AVAILABLE_IN_BROWSER);
                }
                navigator.mediaDevices.getUserMedia({ audio: true })
                    .then(function (stream) {
                        stream.getTracks().forEach(t => t.stop());
                        resolve(STATUS_CODES.GRANTED);
                    })
                    .catch(function () { resolve(STATUS_CODES.DENIED); });
            } else if (key === PERMISSION_KEYS.CAMERA) {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    return resolve(STATUS_CODES.NOT_AVAILABLE_IN_BROWSER);
                }
                navigator.mediaDevices.getUserMedia({ video: true })
                    .then(function (stream) {
                        stream.getTracks().forEach(t => t.stop());
                        resolve(STATUS_CODES.GRANTED);
                    })
                    .catch(function () { resolve(STATUS_CODES.DENIED); });
            } else if (key === PERMISSION_KEYS.NOTIFICATIONS) {
                if (!('Notification' in window)) {
                    return resolve(STATUS_CODES.NOT_AVAILABLE_IN_BROWSER);
                }
                Notification.requestPermission().then(function (perm) {
                    resolve(perm === 'granted' ? STATUS_CODES.GRANTED : STATUS_CODES.DENIED);
                });
            } else {
                resolve(STATUS_CODES.NOT_AVAILABLE_IN_BROWSER);
            }
        });
    }

    // Expose facade on window
    window.KormoKariAndroidPermissions = PermissionsFacade;

    // =========================================================================
    // 3. UI PROMPT MODAL GENERATOR (BRUSHED M3 & KORMOKARI BRAND DESIGN)
    // =========================================================================

    function createPermissionDialog(options) {
        // Options: { title, titleBn, message, messageBn, icon, primaryText, primaryTextBn, onPrimary, secondaryText, onSecondary }
        const existing = document.getElementById('kormokari-perm-modal-container');
        if (existing) existing.remove();

        const container = document.createElement('div');
        container.id = 'kormokari-perm-modal-container';
        container.setAttribute('role', 'dialog');
        container.setAttribute('aria-modal', 'true');
        container.style.cssText = `
            position: fixed;
            top: 0; left: 0; width: 100vw; height: 100vh;
            background: rgba(15, 23, 42, 0.65);
            backdrop-filter: blur(4px);
            -webkit-backdrop-filter: blur(4px);
            z-index: 100000;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 16px;
            box-sizing: border-box;
            animation: kmkFadeIn 220ms ease-out;
        `;

        const card = document.createElement('div');
        card.style.cssText = `
            background: #FFFFFF;
            border-radius: 20px;
            max-width: 440px;
            width: 100%;
            padding: 24px 20px;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
            border: 1px solid rgba(226, 232, 240, 0.8);
            text-align: center;
            box-sizing: border-box;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        `;

        const iconBox = document.createElement('div');
        iconBox.style.cssText = `
            width: 56px;
            height: 56px;
            border-radius: 50%;
            background: #FFF0F5;
            color: #E91E63;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 16px;
            font-size: 26px;
        `;
        iconBox.innerHTML = options.icon || '📍';

        const title = document.createElement('h3');
        title.style.cssText = `
            margin: 0 0 6px 0;
            font-size: 18px;
            font-weight: 700;
            color: #0F172A;
            line-height: 1.3;
        `;
        title.innerText = options.titleBn ? (options.titleBn + ' (' + options.title + ')') : options.title;

        const body = document.createElement('p');
        body.style.cssText = `
            margin: 0 0 20px 0;
            font-size: 14px;
            color: #475569;
            line-height: 1.5;
        `;
        body.innerText = options.messageBn ? (options.messageBn + '\n\n' + options.message) : options.message;

        const actions = document.createElement('div');
        actions.style.cssText = `
            display: flex;
            flex-direction: column;
            gap: 10px;
        `;

        const primaryBtn = document.createElement('button');
        primaryBtn.style.cssText = `
            background: #E91E63;
            color: #FFFFFF;
            border: none;
            border-radius: 12px;
            padding: 14px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            min-height: 48px;
            transition: background 150ms ease;
            box-shadow: 0 4px 6px -1px rgba(233, 30, 99, 0.2);
        `;
        primaryBtn.innerText = options.primaryTextBn ? (options.primaryTextBn + ' (' + options.primaryText + ')') : options.primaryText;
        primaryBtn.onclick = function () {
            primaryBtn.disabled = true;
            primaryBtn.innerText = 'অনুমোদন প্রক্রিয়াধীন… Processing…';
            if (typeof options.onPrimary === 'function') {
                options.onPrimary(container);
            }
        };

        const secondaryBtn = document.createElement('button');
        secondaryBtn.style.cssText = `
            background: #F1F5F9;
            color: #334155;
            border: none;
            border-radius: 12px;
            padding: 12px;
            font-size: 14px;
            font-weight: 500;
            cursor: pointer;
            min-height: 44px;
        `;
        secondaryBtn.innerText = options.secondaryTextBn ? (options.secondaryTextBn + ' / ' + (options.secondaryText || 'Cancel')) : (options.secondaryText || 'Cancel');
        secondaryBtn.onclick = function () {
            container.remove();
            if (typeof options.onSecondary === 'function') {
                options.onSecondary();
            }
        };

        actions.appendChild(primaryBtn);
        actions.appendChild(secondaryBtn);

        card.appendChild(iconBox);
        card.appendChild(title);
        card.appendChild(body);
        card.appendChild(actions);
        container.appendChild(card);
        document.body.appendChild(container);

        return container;
    }

    // =========================================================================
    // 4. WORKER GO-ONLINE PRE-FLIGHT CONTROLLER (HIGHEST PRIORITY)
    // =========================================================================

    let isPreFlightInProgress = false;

    /**
     * Executes the comprehensive pre-flight verification sequence required before
     * marking a Worker Online in KormoKari.
     *
     * Sequence:
     * 1. Detect native Android bridge presence.
     * 2. Query foreground location permission.
     * 3. Prompt user if missing, request ACCESS_FINE_LOCATION.
     * 4. Verify GPS/Location services are enabled.
     * 5. Verify background location if background tracking policy applies.
     * 6. Activate native tracking service: startTracking(authToken).
     * 7. Verify isTrackingActive() confirms tracking success.
     * 8. Proceed with existing online action only after all prerequisites succeed.
     *
     * @param {Object} config Options containing authToken, onReadyToSubmit, onCancel, onError
     */
    function runWorkerOnlinePreFlight(config) {
        config = config || {};

        if (isPreFlightInProgress) {
            console.warn('[KormoKariPermissions] Pre-flight already running. Ignoring duplicate tap.');
            return;
        }
        isPreFlightInProgress = true;

        const resetLock = () => { isPreFlightInProgress = false; };

        // 1. Check if running inside Android app
        const isNative = PermissionsFacade.isNativeBridgeAvailable();

        if (!isNative) {
            // Running in regular desktop or mobile browser.
            // Notify user honestly that native background tracking requires KormoKari Android App,
            // but permit standard browser geolocation if supported.
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(
                    function () {
                        resetLock();
                        if (typeof config.onReadyToSubmit === 'function') {
                            config.onReadyToSubmit({ isNative: false, method: 'browser_geolocation' });
                        }
                    },
                    function (err) {
                        resetLock();
                        alert('লোকেশন সেবা পাওয়া যায়নি। KormoKari অ্যান্ড্রয়েড অ্যাপে সম্পূর্ণ লাইভ ট্র্যাকিং সমর্থিত।\nLocation access is required to go online.');
                        if (typeof config.onError === 'function') config.onError(err);
                    },
                    { timeout: 8000 }
                );
            } else {
                resetLock();
                alert('এই ব্রাউজারে লোকেশন সমর্থিত নয়। অনুগ্রহ করে KormoKari Android অ্যাপ ব্যবহার করুন।');
                if (typeof config.onCancel === 'function') config.onCancel();
            }
            return;
        }

        // Native Android Pre-flight Step 1: Check Foreground Location
        const locStatus = PermissionsFacade.getPermissionStatus(PERMISSION_KEYS.LOCATION_FOREGROUND);

        if (locStatus !== STATUS_CODES.GRANTED) {
            if (locStatus === STATUS_CODES.PERMANENTLY_DENIED) {
                resetLock();
                createPermissionDialog({
                    title: 'Location Permission Required',
                    titleBn: 'লোকেশন অনুমতি প্রয়োজন',
                    message: 'KormoKari requires exact location access so nearby clients can find and hire you. Please enable location permissions in Android Settings.',
                    messageBn: 'কাছাকাছি কাজের অফার এবং ক্লায়েন্টদের কাছে দৃশ্যমান হতে লোকেশন অনুমতি আবশ্যক। অনুগ্রহ করে অ্যান্ড্রয়েড সেটিংস থেকে অনুমতি দিন।',
                    icon: '📍',
                    primaryText: 'Open App Settings',
                    primaryTextBn: 'সেটিংস খুলুন',
                    onPrimary: function (modal) {
                        modal.remove();
                        PermissionsFacade.openAppSettings();
                    },
                    onSecondary: function () {
                        if (typeof config.onCancel === 'function') config.onCancel();
                    }
                });
                return;
            }

            // Prompt user with contextual branded dialog before launching OS prompt
            createPermissionDialog({
                title: 'Enable Location to Go Online',
                titleBn: 'অনলাইনে যেতে লোকেশন অন করুন',
                message: 'Your live location helps connect you with verified jobs and employers nearest to you.',
                messageBn: 'আপনার সঠিক লোকেশন নিকটবর্তী নিয়োগকর্তা ও কাজের সাথে আপনাকে সংযুক্ত করে।',
                icon: '📍',
                primaryText: 'Allow Location',
                primaryTextBn: 'লোকেশন অনুমোদন করুন',
                onPrimary: function (modal) {
                    PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.LOCATION_FOREGROUND)
                        .then(function (newStatus) {
                            modal.remove();
                            if (newStatus === STATUS_CODES.GRANTED) {
                                // Proceed to GPS check
                                proceedWithGpsCheck(config, resetLock);
                            } else {
                                resetLock();
                                alert('লোকেশন অনুমতি ছাড়া অনলাইনে যাওয়া সম্ভব নয়।\nLocation permission is required to accept jobs.');
                                if (typeof config.onError === 'function') config.onError('LOCATION_DENIED');
                            }
                        });
                },
                onSecondary: function () {
                    resetLock();
                    if (typeof config.onCancel === 'function') config.onCancel();
                }
            });
            return;
        }

        // Foreground location is already granted -> Check GPS
        proceedWithGpsCheck(config, resetLock);
    }

    function proceedWithGpsCheck(config, resetLock) {
        // Step 2: Check if device GPS is enabled
        const isGpsOn = PermissionsFacade.isLocationServiceEnabled();
        if (!isGpsOn) {
            resetLock();
            createPermissionDialog({
                title: 'GPS Service is Disabled',
                titleBn: 'ডিভাইস জিপিএস বন্ধ রয়েছে',
                message: 'Device location service (GPS) is turned off. Please turn on location in settings to go online.',
                messageBn: 'আপনার ফোনের জিপিএস / লোকেশন সার্ভিস বন্ধ আছে। অনলাইনে যেতে সেটিংস থেকে জিপিএস চালু করুন।',
                icon: '🛰️',
                primaryText: 'Turn On GPS',
                primaryTextBn: 'জিপিএস চালু করুন',
                onPrimary: function (modal) {
                    modal.remove();
                    PermissionsFacade.openLocationSettings();
                },
                onSecondary: function () {
                    if (typeof config.onCancel === 'function') config.onCancel();
                }
            });
            return;
        }

        // Step 3: Check Background Location (Android 10+)
        const bgStatus = PermissionsFacade.getPermissionStatus(PERMISSION_KEYS.LOCATION_BACKGROUND);
        if (bgStatus !== STATUS_CODES.GRANTED && bgStatus !== STATUS_CODES.NOT_AVAILABLE_IN_BROWSER) {
            // Optional: Request background location or guide user
            // If background location is not mandatory for initial online state, we proceed,
            // but if required by enterprise tracking, show guidance:
            console.log('[KormoKariPermissions] Foreground location verified. Background status:', bgStatus);
        }

        // Step 4: Start Native Tracking Service
        const token = config.authToken || window.KORMOKARI_AUTH_TOKEN || '';
        PermissionsFacade.startTracking(token).then(function (started) {
            const trackingStatus = PermissionsFacade.getTrackingStatus();
            console.log('[KormoKariPermissions] Tracking started result:', started, trackingStatus);

            resetLock();

            // All prerequisites confirmed!
            if (typeof config.onReadyToSubmit === 'function') {
                config.onReadyToSubmit({
                    isNative: true,
                    trackingStarted: started,
                    trackingStatus: trackingStatus
                });
            }
        });
    }

    // =========================================================================
    // 5. CONTEXTUAL PERMISSION WIDGET BINDERS
    // =========================================================================

    /**
     * Binds click handlers to contextual permission buttons across the website.
     */
    function bindContextualButtons() {
        // 1. Worker Go-Online triggers: [data-kormokari-action="go-online"]
        document.querySelectorAll('[data-kormokari-action="go-online"]').forEach(btn => {
            if (btn.dataset.kmkBound) return;
            btn.dataset.kmkBound = 'true';

            btn.addEventListener('click', function (e) {
                // If it's a form submit or toggle button, intercept
                if (btn.dataset.kmkVerified === 'true') {
                    // Already verified by pre-flight, let form submit naturally
                    btn.dataset.kmkVerified = 'false';
                    return;
                }

                e.preventDefault();
                e.stopPropagation();

                const originalText = btn.innerText;
                btn.disabled = true;
                btn.innerText = 'যাচাই করা হচ্ছে… Verifying…';

                runWorkerOnlinePreFlight({
                    authToken: btn.dataset.authToken || window.KORMOKARI_AUTH_TOKEN,
                    onReadyToSubmit: function () {
                        btn.dataset.kmkVerified = 'true';
                        btn.disabled = false;
                        btn.innerText = originalText;
                        // Trigger native click or submit form
                        if (btn.form) {
                            btn.form.submit();
                        } else {
                            btn.click();
                        }
                    },
                    onCancel: function () {
                        btn.disabled = false;
                        btn.innerText = originalText;
                    },
                    onError: function () {
                        btn.disabled = false;
                        btn.innerText = originalText;
                    }
                });
            });
        });

        // 2. Nearby Jobs / Location buttons: [data-kormokari-perm="location"]
        document.querySelectorAll('[data-kormokari-perm="location"]').forEach(btn => {
            if (btn.dataset.kmkBound) return;
            btn.dataset.kmkBound = 'true';

            btn.addEventListener('click', function (e) {
                const currentStatus = PermissionsFacade.getPermissionStatus(PERMISSION_KEYS.LOCATION_FOREGROUND);
                if (currentStatus === STATUS_CODES.GRANTED) {
                    if (!PermissionsFacade.isLocationServiceEnabled()) {
                        PermissionsFacade.openLocationSettings();
                    } else {
                        // Refresh location data
                        window.dispatchEvent(new CustomEvent('kormokari:locationReady'));
                    }
                    return;
                }

                e.preventDefault();
                PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.LOCATION_FOREGROUND)
                    .then(function (status) {
                        if (status === STATUS_CODES.GRANTED) {
                            window.dispatchEvent(new CustomEvent('kormokari:locationReady'));
                            btn.style.display = 'none';
                        } else if (status === STATUS_CODES.PERMANENTLY_DENIED) {
                            PermissionsFacade.openAppSettings();
                        }
                    });
            });
        });

        // 3. Notification alert buttons: [data-kormokari-perm="notifications"]
        document.querySelectorAll('[data-kormokari-perm="notifications"]').forEach(btn => {
            if (btn.dataset.kmkBound) return;
            btn.dataset.kmkBound = 'true';

            btn.addEventListener('click', function (e) {
                e.preventDefault();
                PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.NOTIFICATIONS)
                    .then(function (status) {
                        updateNotificationButtonState(btn, status);
                        if (status === STATUS_CODES.PERMANENTLY_DENIED) {
                            PermissionsFacade.openNotificationSettings();
                        }
                    });
            });

            // Initial state inspection
            const current = PermissionsFacade.getPermissionStatus(PERMISSION_KEYS.NOTIFICATIONS);
            updateNotificationButtonState(btn, current);
        });

        // 4. Voice recording microphone buttons: [data-kormokari-perm="microphone"]
        document.querySelectorAll('[data-kormokari-perm="microphone"]').forEach(btn => {
            if (btn.dataset.kmkBound) return;
            btn.dataset.kmkBound = 'true';

            btn.addEventListener('click', function (e) {
                const status = PermissionsFacade.getPermissionStatus(PERMISSION_KEYS.MICROPHONE);
                if (status === STATUS_CODES.GRANTED) {
                    // Allowed to record
                    return;
                }

                e.preventDefault();
                PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.MICROPHONE)
                    .then(function (newStatus) {
                        if (newStatus === STATUS_CODES.GRANTED) {
                            window.dispatchEvent(new CustomEvent('kormokari:microphoneGranted'));
                        } else if (newStatus === STATUS_CODES.PERMANENTLY_DENIED) {
                            PermissionsFacade.openAppSettings();
                        }
                    });
            });
        });

        // 5. Camera capture buttons: [data-kormokari-perm="camera"]
        document.querySelectorAll('[data-kormokari-perm="camera"]').forEach(btn => {
            if (btn.dataset.kmkBound) return;
            btn.dataset.kmkBound = 'true';

            btn.addEventListener('click', function (e) {
                const status = PermissionsFacade.getPermissionStatus(PERMISSION_KEYS.CAMERA);
                if (status === STATUS_CODES.GRANTED) {
                    return;
                }

                e.preventDefault();
                PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.CAMERA)
                    .then(function (newStatus) {
                        if (newStatus === STATUS_CODES.GRANTED) {
                            window.dispatchEvent(new CustomEvent('kormokari:cameraGranted'));
                        } else if (newStatus === STATUS_CODES.PERMANENTLY_DENIED) {
                            PermissionsFacade.openAppSettings();
                        }
                    });
            });
        });
    }

    function updateNotificationButtonState(btn, status) {
        if (status === STATUS_CODES.GRANTED) {
            btn.innerText = 'নোটিফিকেশন সক্রিয় (Notifications Enabled)';
            btn.classList.add('kmk-perm-granted');
            btn.disabled = true;
        } else {
            btn.innerText = 'নোটিফিকেশন চালু করুন (Enable Notifications)';
            btn.classList.remove('kmk-perm-granted');
            btn.disabled = false;
        }
    }

    // =========================================================================
    // 6. CENTRAL APP PERMISSIONS SETTINGS TABLE RENDERER
    // =========================================================================

    /**
     * Renders or refreshes the App Permissions section in Worker & Employer Settings.
     * Container selector: #kormokari-app-permissions-container
     */
    function renderCentralPermissionsCard() {
        const container = document.getElementById('kormokari-app-permissions-container');
        if (!container) return;

        const isNative = PermissionsFacade.isNativeBridgeAvailable();
        const statuses = PermissionsFacade.getAllPermissionStatuses();
        const isGpsOn = PermissionsFacade.isLocationServiceEnabled();
        const isTracking = PermissionsFacade.isTrackingActive();

        const items = [
            {
                id: 'notifications',
                icon: '🔔',
                title: 'Notifications',
                titleBn: 'নোটিফিকেশন ও কাজের অ্যালার্ট',
                status: statuses[PERMISSION_KEYS.NOTIFICATIONS],
                action: () => PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.NOTIFICATIONS),
                settingsAction: () => PermissionsFacade.openNotificationSettings()
            },
            {
                id: 'location_fg',
                icon: '📍',
                title: 'Foreground Location',
                titleBn: 'লাইভ লোকেশন',
                status: statuses[PERMISSION_KEYS.LOCATION_FOREGROUND],
                action: () => PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.LOCATION_FOREGROUND),
                settingsAction: () => PermissionsFacade.openAppSettings()
            },
            {
                id: 'gps',
                icon: '🛰️',
                title: 'GPS Location Service',
                titleBn: 'ডিভাইস জিপিএস সেবা',
                status: isGpsOn ? STATUS_CODES.GRANTED : STATUS_CODES.GPS_DISABLED,
                action: () => PermissionsFacade.openLocationSettings(),
                settingsAction: () => PermissionsFacade.openLocationSettings()
            },
            {
                id: 'location_bg',
                icon: '🗺️',
                title: 'Background Location',
                titleBn: 'ব্যাকগ্রাউন্ড লোকেশন',
                status: statuses[PERMISSION_KEYS.LOCATION_BACKGROUND],
                action: () => PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.LOCATION_BACKGROUND),
                settingsAction: () => PermissionsFacade.openAppSettings()
            },
            {
                id: 'camera',
                icon: '📷',
                title: 'Camera',
                titleBn: 'ক্যামেরা (KYC ও কাজের ছবি)',
                status: statuses[PERMISSION_KEYS.CAMERA],
                action: () => PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.CAMERA),
                settingsAction: () => PermissionsFacade.openAppSettings()
            },
            {
                id: 'microphone',
                icon: '🎙️',
                title: 'Microphone',
                titleBn: 'মাইক্রোফোন (ভয়েস মেসেজ)',
                status: statuses[PERMISSION_KEYS.MICROPHONE],
                action: () => PermissionsFacade.requestRuntimePermission(PERMISSION_KEYS.MICROPHONE),
                settingsAction: () => PermissionsFacade.openAppSettings()
            },
            {
                id: 'tracking',
                icon: '⚡',
                title: 'Live Tracking Service',
                titleBn: 'লাইভ ওয়ার্কার ট্র্যাকিং সার্ভিস',
                status: isTracking ? STATUS_CODES.GRANTED : STATUS_CODES.DENIED,
                customLabel: isTracking ? 'Active (সক্রিয়)' : 'Inactive (নিষ্ক্রিয়)',
                action: () => PermissionsFacade.openLocationSettings(),
                settingsAction: () => PermissionsFacade.openLocationSettings()
            },
            {
                id: 'battery',
                icon: '🔋',
                title: 'Battery Optimization',
                titleBn: 'ব্যাটারি অপ্টিমাইজেশন ছাড়',
                status: STATUS_CODES.SETTINGS_REQUIRED,
                action: () => PermissionsFacade.requestBatteryOptimizationExemption(),
                settingsAction: () => PermissionsFacade.openBatteryOptimizationSettings()
            }
        ];

        let html = `
            <div class="kormokari-perm-card" style="background: #FFFFFF; border-radius: 16px; border: 1px solid #E2E8F0; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; border-bottom: 1px solid #F1F5F9; padding-bottom: 12px;">
                    <div>
                        <h4 style="margin: 0; font-size: 16px; font-weight: 700; color: #0F172A;">App Permissions (অ্যাপ অনুমতিসমূহ)</h4>
                        <p style="margin: 4px 0 0 0; font-size: 13px; color: #64748B;">
                            ${isNative ? 'KormoKari Android Bridge Connected' : 'Running in Web Browser (অ্যান্ড্রয়েড অ্যাপ প্রয়োজন)'}
                        </p>
                    </div>
                    <button id="kmk-perm-refresh-btn" style="background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 6px 12px; font-size: 12px; font-weight: 600; cursor: pointer; color: #334155;">
                        🔄 Refresh
                    </button>
                </div>
                <div style="display: flex; flex-direction: column; gap: 12px;">
        `;

        items.forEach(item => {
            const isGranted = item.status === STATUS_CODES.GRANTED;
            const isGpsOff = item.status === STATUS_CODES.GPS_DISABLED;
            const isPermDenied = item.status === STATUS_CODES.PERMANENTLY_DENIED;

            let badgeBg = '#FEF2F2';
            let badgeColor = '#EF4444';
            let badgeText = item.customLabel || 'Not Allowed';

            if (isGranted) {
                badgeBg = '#ECFDF5';
                badgeColor = '#10B981';
                badgeText = item.customLabel || 'Allowed (অনুমোদিত)';
            } else if (isGpsOff) {
                badgeBg = '#FFFBEB';
                badgeColor = '#D97706';
                badgeText = 'GPS Disabled (জিপিএস বন্ধ)';
            } else if (isPermDenied) {
                badgeBg = '#FEF2F2';
                badgeColor = '#B91C1C';
                badgeText = 'Settings Required';
            } else if (item.status === STATUS_CODES.NOT_AVAILABLE_IN_BROWSER) {
                badgeBg = '#F1F5F9';
                badgeColor = '#64748B';
                badgeText = 'App Required';
            }

            html += `
                <div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: #F8FAFC; border-radius: 12px; border: 1px solid #EDF2F7;">
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="font-size: 20px;">${item.icon}</span>
                        <div>
                            <div style="font-size: 14px; font-weight: 600; color: #1E293B;">${item.titleBn}</div>
                            <div style="font-size: 12px; color: #64748B;">${item.title}</div>
                        </div>
                    </div>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="display: inline-block; padding: 4px 8px; border-radius: 6px; font-size: 12px; font-weight: 600; background: ${badgeBg}; color: ${badgeColor};">
                            ${badgeText}
                        </span>
                        ${!isGranted ? `
                            <button data-perm-id="${item.id}" class="kmk-perm-action-btn" style="background: #E91E63; color: #FFFFFF; border: none; border-radius: 6px; padding: 6px 10px; font-size: 12px; font-weight: 600; cursor: pointer;">
                                ${isPermDenied || isGpsOff ? 'Settings' : 'Allow'}
                            </button>
                        ` : ''}
                    </div>
                </div>
            `;
        });

        html += `
                </div>
                <div style="margin-top: 16px; padding-top: 12px; border-top: 1px solid #F1F5F9; text-align: center;">
                    <button id="kmk-perm-open-settings-main" style="background: transparent; border: none; color: #E91E63; font-size: 13px; font-weight: 600; cursor: pointer; text-decoration: underline;">
                        Open Android System Settings for KormoKari (অ্যান্ড্রয়েড সেটিংস খুলুন)
                    </button>
                </div>
            </div>
        `;

        container.innerHTML = html;

        // Attach listeners
        const refreshBtn = document.getElementById('kmk-perm-refresh-btn');
        if (refreshBtn) refreshBtn.onclick = () => renderCentralPermissionsCard();

        const mainSettingsBtn = document.getElementById('kmk-perm-open-settings-main');
        if (mainSettingsBtn) mainSettingsBtn.onclick = () => PermissionsFacade.openAppSettings();

        container.querySelectorAll('.kmk-perm-action-btn').forEach(btn => {
            const id = btn.dataset.permId;
            const targetItem = items.find(i => i.id === id);
            if (targetItem) {
                btn.onclick = () => {
                    const res = (targetItem.status === STATUS_CODES.PERMANENTLY_DENIED || targetItem.status === STATUS_CODES.GPS_DISABLED)
                        ? targetItem.settingsAction()
                        : targetItem.action();
                    if (res && typeof res.then === 'function') {
                        res.then(() => renderCentralPermissionsCard());
                    } else {
                        setTimeout(() => renderCentralPermissionsCard(), 1200);
                    }
                };
            }
        });
    }

    // =========================================================================
    // 7. INITIALIZATION & LIFECYCLE LISTENERS
    // =========================================================================

    function init() {
        bindContextualButtons();
        renderCentralPermissionsCard();

        // Listen for visibility / window focus to automatically refresh statuses when user returns from Settings
        if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
            window.addEventListener('focus', function () {
                renderCentralPermissionsCard();
                bindContextualButtons();
            });
        }

        if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
            document.addEventListener('visibilitychange', function () {
                if (document.visibilityState === 'visible') {
                    renderCentralPermissionsCard();
                    bindContextualButtons();
                }
            });
        }
    }

    if (typeof document !== 'undefined' && document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Expose runners globally for direct Blade template event handlers
    window.KormoKariRunWorkerOnlinePreFlight = runWorkerOnlinePreFlight;
    window.KormoKariRenderCentralPermissionsCard = renderCentralPermissionsCard;

})(window, document);
